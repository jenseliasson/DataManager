
all:
	mvn package

install:
	mvn install

run:
	java -jar target/DataManager-1.0.0-jar-with-dependencies.jar

clean:
	mvn clean

