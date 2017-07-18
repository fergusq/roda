# Röda

## English

Röda is a homemade scripting language inspired by Bourne shell, Ruby and others. While not being a real shell language, Röda
still makes an extensive use of concurrency and streams (pipes). For more documentation, see GUIDE.md.
The standard library reference and other information is also found at the authors [web page](http://iikka.kapsi.fi/roda/). 

### Building

Using Gradle:

```sh
$ git clone --recursive https://github.com/fergusq/roda.git
$ cd roda
roda $ gradle fatJar
```

### Example

Real life examples:

* Small scripts at my [gist page](https://gist.github.com/fergusq) - I use Röda very often to create small scripts that could have been written in sh, AWK or Perl.
* [Static site generator](https://github.com/fergusq/plan)
* [Mafia game master service](https://github.com/fergusq/mafia)
* [Lyrics video generator](https://github.com/fergusq/videolyrics)

Prime generator:

```sh
#!/usr/bin/röda

main {
	primes := [2]
	seq 3, 10000 | { primes += i if [ i % p != 0 ] for p in primes } for i
	print p for p in primes
}
```

HTTP server:

```sh
#!/usr/bin/röda

{
	http := require("http_server")
}

main {
	server := new http.HttpServer(8080)
	server.controllers["/"] = http.controller({ |request|
		request.send "200 OK", "<html><head><title>Hello world!</title></head><body>Hello world!</body></html>"
	})
	while true; do
		server.update
	done
}
```

## Suomeksi

Röda on uusi ohjelmointikieleni, joka on saanut vaikutteensa lähinnä Bourne shellistä.
Dokumentaatio on tällä hetkellä saatavilla suomeksi tiedostossa OHJEET.md.

## LICENSE

    Röda Interpreter
    Copyright (C) 2017 Iikka Hauhio

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
