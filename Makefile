all: classes

classes:
	javac -cp nept -sourcepath src -d bin -Xlint src/org/kaivos/röda/Röda.java

röda.jar: classes
	jar cvfm röda.jar Manifest.txt -C bin/ . -C nept/ org/kaivos/nept/
