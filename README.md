# Röda

*English:*

Röda is a homemade scripting language inspired by Bourne shell, Ruby and others. While not being a real shell language, Röda
still makes an extensive use of concurrency and streams (pipes).

An example:

```sh
#!/usr/bin/röda

main {
	import "telegram.röd"
	token := "<bot identifier>"
	bot := ![tg_init token]
	bot.on_message = { |chat msg|
		if [ msg =~ "/time(@TimeBot)?" ]; do
			bot.send_message chat !(exec -l "date")[0]
		done
	}
	while true; do
		try bot.update
	done
}
```

*Suomeksi:*

Röda on uusi ohjelmointikieleni, joka on saanut vaikutteensa lähinnä Bourne shellistä.
Dokumentaatio on saatavilla tällä hetkellä vain suomeksi tiedostossa OHJEET.md.
