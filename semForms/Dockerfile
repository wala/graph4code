# syntax=docker/dockerfile:1

FROM eclipse-temurin:latest

WORKDIR /app

# Uncomment after testing
RUN wget https://archive.org/download/graph4codev1/codebreaker3.jar

COPY elastic/http_ca.crt .

CMD ["java", "-cp", "codebreaker3.jar", "com.ibm.wala.codeBreaker.turtleServer.TurtleWrapper"]