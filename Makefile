all: classes

classes:
	javac -cp nept/bin -sourcepath src -d bin -Xlint src/org/kaivos/röda/Röda.java

röda.jar: classes
	jar cvfm röda.jar Manifest.txt -C bin/ . -C nept/bin/ org/kaivos/nept/
