.PHONY: default run build clean
default: run

run: build
	java -jar ExplodingKittens.jar

build: ExplodingKittens.jar

ExplodingKittens.jar: ExplodingKittens.kt
	../kotlin/bin/kotlinc ExplodingKittens.kt -include-runtime -d ExplodingKittens.jar

clean:
	rm -fv ./*.jar
