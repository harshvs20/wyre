package com.wyre.snapcard.exchange;

import com.wyre.snapcard.exchange.model.ExchangeConstants;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.ext.mongo.MongoClient;
import io.vertx.rxjava.ext.web.Route;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.IntStream;

import static io.vertx.core.http.HttpHeaders.CACHE_CONTROL;

/**
 * Created by hshrivastava on 2/25/17.
 */
public class WyreSnapCardExchangeVerticle extends io.vertx.rxjava.core.AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(WyreSnapCardExchangeVerticle.class);
    private io.vertx.rxjava.ext.mongo.MongoClient mongoClient;
    private static final String EXCHANGE_COLLECTION = "exchangeRates";

    private static final String BASE_URL = "/wyre/v1";

    private String OkBaseURL;
    private String bitBaseURL;

    private HttpClient httpClient;
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");


    @Override
    public void start() throws Exception {
        io.vertx.rxjava.ext.web.Router router = io.vertx.rxjava.ext.web.Router.router(vertx);

        router.route().handler(io.vertx.rxjava.ext.web.handler.BodyHandler.create());
        router.route().handler(io.vertx.rxjava.ext.web.handler.CookieHandler.create());

        addSwaggerRoute(router);
        addHealthRoutes(router);
        addRoutes(router);

        mongoClient = getMongoClient(config());
        OkBaseURL = config().getJsonObject("OkCoinUS").getString("path");
        bitBaseURL = config().getJsonObject("BitFinex").getString("path");
        httpClient = createHttpClient();

        int httpPort = config().getJsonObject("http_config", new JsonObject()).getInteger("port", 8080);
        vertx.createHttpServer().requestHandler(router::accept).listen(httpPort, ar -> {
            if(ar.failed()){
                logger.error("Failed to start Wyre Main Verticle", ar.cause());
            } else {
                System.out.println("SnapCard Main verticle started on port : " + ar.result().actualPort());
                for(Route r : router.getRoutes()){
                    System.out.println("Known Route :" + r.getPath() + " on ");
                }
                logger.info("SnapcardExchangeService started on port : " + ar.result().actualPort());
            }
        });
    }

    private HttpClient createHttpClient(){
        HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setSsl(true));
        return httpClient;
    }

    private void seedData(RoutingContext routingContext){
        int limit = Integer.parseInt(routingContext.request().getParam("limit"));
        IntStream.range(0, limit).forEach(i -> {
            httpClient.getAbs(OkBaseURL, res -> {
                if(res.statusCode()==200){
                    res.bodyHandler(b -> {
                        JsonObject response = b.toJsonObject();
                        JsonObject data = new JsonObject();

                        data.put(ExchangeConstants.BID.getName(),Double.parseDouble(response.getJsonObject("ticker").getString("buy")));
                        data.put(ExchangeConstants.ASK.getName(),Double.parseDouble(response.getJsonObject("ticker").getString("sell")));
                        data.put(ExchangeConstants.LAST.getName(),Double.parseDouble(response.getJsonObject("ticker").getString("last")));
                        data.put(ExchangeConstants.EXCHANGE.getName(),"OkCoinUS");
                        data.put(ExchangeConstants.DATE.getName(),Long.parseLong(response.getString("date")));

                        mongoSave(data);
                    });

                }
                res.exceptionHandler(e -> {
                    System.out.println(e.getMessage());
                });
            }).end();
            httpClient.getAbs(bitBaseURL, res -> {
                if(res.statusCode()==200){
                    res.bodyHandler(b -> {
                        JsonObject response = b.toJsonObject();
                        JsonObject data = new JsonObject();

                        data.put(ExchangeConstants.BID.getName(),Double.parseDouble(response.getString("bid")));
                        data.put(ExchangeConstants.ASK.getName(),Double.parseDouble(response.getString("ask")));
                        data.put(ExchangeConstants.LAST.getName(),Double.parseDouble(response.getString("last_price")));
                        data.put(ExchangeConstants.EXCHANGE.getName(),"BitFinex");
                        data.put(ExchangeConstants.DATE.getName(),Long.parseLong(response.getString("timestamp").substring(0, response.getString("timestamp").indexOf("."))));

                        mongoSave(data);
                    });
                }
            }).end();
        });
        routingContext.response().end("Data will be seeded in the backgroup");
    }

    private void mongoSave(JsonObject jsonObject){
        mongoClient.save(EXCHANGE_COLLECTION, jsonObject, result -> {
            if(result.succeeded()){
                logger.info(" posted to mongo");
            }else{
                logger.error("Error while posting to mongo" + result.cause());
            }
        });
    }

    private void addRoutes(Router router){
        logger.info("Adding routes");
        router.get(BASE_URL + "/rates/average/seedData").handler(this::seedData);
        router.get(BASE_URL + "/rates/average").handler(this::handleAverage);
        router.get(BASE_URL + "/rates/average/:time").handler(this::handleAverageAtTime);
        router.get(BASE_URL + "/rates/average/:time/:exchange").handler(this::handleAverageExchangeAtTime);
    }

    private void addHealthRoutes(Router router) {
        router.get("/rates/health").handler(this::healthRest);
    }

    private void handleAverage(RoutingContext routingContext){
        JsonObject command = new JsonObject()
                .put("aggregate", EXCHANGE_COLLECTION)
                .put("pipeline", new JsonArray().add(
                        new JsonObject().put("$group",
                                new JsonObject()
                                        .put("_id", "")
                                        .put("bid", new JsonObject().put("$avg", "$bid"))
                                        .put("ask", new JsonObject().put("$avg", "$ask"))
                                        .put("last", new JsonObject().put("$avg", "$last"))
                        )));

        runCommand(command, routingContext);
    }

    private void handleAverageAtTime(RoutingContext routingContext){

        try{
            Date date = (Date)formatter.parse(routingContext.request().getParam("time"));
            long mills = date.getTime();
            JsonObject command = new JsonObject()
                    .put("aggregate", EXCHANGE_COLLECTION)
                    .put("pipeline", new JsonArray().add(new JsonObject().put("$match",
                            new JsonObject()
                                    .put("date", new JsonObject()
                                            .put("$lte", mills))))
                            .add(new JsonObject().put("$group",
                                    new JsonObject()
                                            .put("_id", "")
                                            .put("bid", new JsonObject().put("$avg", "$bid"))
                                            .put("ask", new JsonObject().put("$avg", "$ask"))
                                            .put("last", new JsonObject().put("$avg", "$last"))
                            )));

            runCommand(command, routingContext);
        }catch (Exception e){
            logger.error("Exception while parsing date");
            routingContext.response().setStatusCode(404).end("Bad Request. Change date format to yyyy-MM-dd");
        }
    }

    private void handleAverageExchangeAtTime(RoutingContext routingContext){
        try{
            Date date = (Date)formatter.parse(routingContext.request().getParam("time"));
            long mills = date.getTime();
            String exchange = routingContext.request().getParam("exchange");
            JsonObject command = new JsonObject()
                    .put("aggregate", EXCHANGE_COLLECTION)
                    .put("pipeline", new JsonArray().add(new JsonObject().put("$match",
                            new JsonObject()
                                    .put("date", new JsonObject()
                                            .put("$lte", mills))
                                    .put("exchange", exchange)))
                            .add(new JsonObject().put("$group",
                                    new JsonObject()
                                            .put("_id", "")
                                            .put("bid", new JsonObject().put("$avg", "$bid"))
                                            .put("ask", new JsonObject().put("$avg", "$ask"))
                                            .put("last", new JsonObject().put("$avg", "$last"))
                            )));

            runCommand(command, routingContext);
        }catch (Exception e){
            logger.error("Exception while parsing date");
            routingContext.response().setStatusCode(404).end("Bad Request. Change date format to yyyy-MM-dd");
        }
    }



    private void addSwaggerRoute(Router router){
        logger.info("Adding swagger routes");
        router.route(BASE_URL + "/swagger/*").handler(StaticHandler.create("webroot/v1/swagger"));
    }

    private void healthRest(RoutingContext routingContext){
        logger.debug("health check");
        routingContext.response().
                putHeader(String.valueOf(CACHE_CONTROL), "no-cache").
                end("OK");
    }

    private MongoClient getMongoClient(JsonObject config){
        JsonObject mongoConfig = config.getJsonObject("mongo_config");
        return MongoClient.createShared(vertx, mongoConfig);
    }

    private void runCommand(JsonObject command, RoutingContext routingContext){
        mongoClient.runCommand("aggregate", command, res -> {
            if(res.succeeded()){
                JsonObject reply = res.result().getJsonArray("result").getList().size() > 0 ? (JsonObject) res.result().getJsonArray("result").getList().get(0) : new JsonObject();
                reply.remove("_id");
                routingContext.response().end(reply.encodePrettily());
            }
        });
    }

}
