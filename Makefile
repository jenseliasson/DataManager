
all:
	mvn package

install:
	mvn install

run:
	java -Dlog4j.configurationFile=./log4j2.xml -jar target/DataManager-1.0.0-jar-with-dependencies.jar

clean:
	mvn clean

