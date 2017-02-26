# Wyre : Snapcard Exchange

### Swagger Documentation and UI
```
http://localhost:8080/wyre/v1/swagger/ui/#/
```

### Running the application

- Using Gradle:

```bash
gradle clean shadowJar
```
```bash
java -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=9987 -jar build/libs/-*fat.jar -conf src/main/conf/app-config.json
```