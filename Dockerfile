FROM ubuntu:latest
RUN apt-get update
RUN apt-get install -y openjdk-8-jdk

RUN useradd -m evaluator
USER evaluator

COPY server/target/scala-2.11/evaluator-server.jar evaluator.jar
CMD java -Dhttp.port=$PORT -jar evaluator.jar
