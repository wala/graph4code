# syntax=docker/dockerfile:1

FROM eclipse-temurin:latest

WORKDIR /app

# Uncomment after testing
RUN wget https://archive.org/download/graph4codev1/CodeBreaker_py3-0.0.1-SNAPSHOT.jar

COPY elastic/http_ca.crt .

CMD ["java", "-cp", "CodeBreaker_py3-0.0.1-SNAPSHOT.jar", "com.ibm.wala.codeBreaker.turtleServer.TurtleWrapper"]
