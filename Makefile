JACKSON = lib/jackson-annotations-2.17.2.jar:lib/jackson-core-2.17.2.jar:lib/jackson-databind-2.17.2.jar

SRC = $(wildcard *.java)

MAIN = NewsAggregator

build:
	javac -cp ".:$(JACKSON)" -d . $(SRC)

run:
	java -cp ".:$(JACKSON)" $(MAIN) $(ARGS)

clean:
	rm -f *.class
	rm -f *.txt