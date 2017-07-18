# Röda

Röda on uusi ohjelmointikieleni! Se perustuu syntaksiltaan ja ominaisuuksiltaan sh-skripteihin.

## Käyttäminen

Röda vaati toimiakseen [Nept-kirjaston](https://github.com/fergusq/nept).

Neptin ja Rödan kääntäminen Gradlella:

```sh
$ git clone --recursive https://github.com/fergusq/roda.git
$ cd roda
roda $ gradle fatJar
```

Rödan mukana tulee interaktiivinen tulkki, jota voi käyttää valitsimella `-i`.

```sh
$ java -jar röda.jar -i
```

## Perustietoja

Röda-funktiolla on parametrit, sekä sisään- ja ulostulo, jotka ovat jonoja. Funktio ei varsinaisesti
"palauta" mitään arvoa, vaan funktion paluuarvo voidaan ajatella listana kaikista sen ulostulovirran
arvoista. Funktio voi lukea sisääntuloaan `pull`-komennolla ja työntää ulostuloon arvoja `push`-komennolla.

`main`-funktion sisään- ja ulostulo on kytketty standardisyötteeseen ja -tulosteeseen.

Alla on yksinkertainen duplicate-funktio, joka lukee yhden arvon ja palauttaa kaksi arvoa. Koska sitä
kutsutaan `main`-funktiosta, lukee se syötettä suoraan standardisyötteestä.
```sh
duplicate {
	pull value
	push value
	push value
}

main {
	push "Syötä tekstiä: "
	duplicate
}
```

Funktioita voi putkittaa `|`-operaattorilla, jolloin niiden sisään- ja ulostulovirrat kytketään toisiinsa.
Seuraava ohjelma tulostaa kahtena tiedoston "tieto.txt" ensimmäisen rivin:

```sh
readLines "tieto.txt" | duplicate
```

Jos kaikki rivit haluttaisiin kahdentaa, olisi tehtävä uusi versio duplicate-funktiosta, joka lukee
kaiken mahdollisen syötteen:

```sh
duplicate {
	for value do
		push value
		push value
	done
}
```

`for`-silmukka lukee sisääntulovirrasta arvoja, kunnes niitä ei enää ole.

## Perussyntaksi

### Rivinvaihdot

Joissakin kohdissa on oltava rivinvaihto.

- Lauseet erotellaan toisistaan rivinvaihdoilla.
- Nimettömän funktion parametrilistan jälkeen on oltava rivinvaihto.

Rivinvaihtojen tilalla voi käyttää `;`-merkkiä ja toisinpäin.

### Kommentit

Röda käyttää C-tyylisiä `/* ... */`-kommentteja.

### Funktiot

Röda-ohjelma on joukko määritelmiä, usein funktioita:
```sh
funktio1 parametrit {

}

funktio2 parametrit {

}
```

#### Vaihteleva määrä argumentteja

Funktiolla voi ottaa vaihtelevan määrän argumentteja, jolloin viimeiselle parametrille pitää antaa
`...`-määrite:

```sh
duplicate_files files... {
	for file in files do
		readLines file | duplicate
	done
}
```

#### Viittausparametrit

Funktio voi ottaa myös muuttujaviittauksen, jolloin ko. parametrille pitää antaa `&`-määrite. Seuraava funktio
lukee kaksi arvoa, tekee niistä listan ja asettaa sen muuttujaan.

```sh
pull_twice &variable {
	pull value1
	pull value2
	variable = [value1, value2]
}
```

#### Parametrin tyyppi

Parametrille voi määritellä tyypin, joka tarkistetaan aina funktiota kutsuttaessa. Viittausparametreille
ei voi kuitenkaan vielä määritellä tyyppiä.

```sh
kappale(sisältö : string) {
	push "<p>"..sisältö.."</p>"
}
```

#### Parametrin oletusarvo

Funktion viimeisille parametreille voi antaa oletusarvon, jota käytetään, jos parametria vastaavaa argumenttia ei ole annettu.
Oletusarvoparametreja vastaavat argumentit täytyy antaa nimettyinä argumentteina.

```sh
korostus sisältö, väri="#ff0000" {
	push "<span style='color:"..väri..";'>"..sisältö.."</span>"
}
```

Oletusarvoparametreja voi määritellä myös `...`-merkkien jälkeen.

#### Tyyppiparametrit

Muuttujaparametrien lisäksi funktiolla voi olla tyyppiparametreja, joille pitää antaa funktiokutsussa
arvot muiden parametrien tapaan:

```c
init_list<<T>> &variable {
	variable := new list<<T>>
}
```

### Tietueet

Ohjelman ylätasolla voi funktioiden lisäksi esiintyä tietueitamäärityksiä.
Tietueet ovat tapa säilöä useita arvoja yhteen olioon.
Ne ovat vahvasti tyypitettyjä ja siksi turvallisempia kuin tyypittämättömät listat ja hajautuskartat.

Tietuemääritys koostuu nimestä ja joukosta kenttiä:

```
record Perhe {
	nimi : string
	osoite : string
	jäsenet : list
}
```

Kun tietue luodaan, sen kentät ovat oletuksena määrittelemättömiä. Niihin on asetettava arvo ennen, kuin niitä
voi kunnolla käyttää.

```sh
perhe := new Perhe
perhe.nimi = "Harakka"
perhe.osoite = "Lumipolku 41 A 7"
perhe.jäsenet = ["Miete", "Joona", "Linn"]
```

Jos haluaa, osalle kentistä voi antaa oletusarvoja. Oletusarvolausekkeet suoritetaan uudestaan aina, kun uusi
tietue luodaan. Niiden sisään- ja ulostulovirrat ovat kiinni ja niiden näkyvyysalue on sama kuin ylätasolla.

```
record Perhe {
	nimi : string
	osoite : string
	jäsenet : list = []
}
```

Tietueilla voi olla tyyppiparametreja, joiden avulla tietyn kentän tyypin voi päättää oliota luodessa:

```
record LinkattuLista<<T>> {
	arvo : T
	linkki : LinkattuLista<<T>>
}
```

### Lauseet

Funktioiden sisällä on lauseita, jotka koostuvat yhdestä tai useammasta putkitetusta komennosta.
Putki yhdistää komentojen ulos- ja sisääntulot toisiinsa. Lauseen ensimmäisen ja viimeisen komennon
sisään- ja ulostulo on kytketty isäntäfunktion sisään- ja ulostuloon.

```sh
komento1 argumentit | komento2 argumentit | komento3 argumentit
```

### Komennot

Komento voi olla joko funktiokutsu, muuttujakomento tai ohjausrakenne.

#### Funktiokutsu

Funktiokutsu koostuu funktiosta ja argumenteista, jotka erotellaan pilkuilla.
Myös muitakin arvoja kuin funktioita voidaan kutsua ikään, kuin ne olisivat funktioita. Nämä
erityistapaukset on alempana.

Funktion argumentteina mahdollisesti olevat funktiokutsut on kytketty isäntäfunktion virtaan, eikä
putkeen. Vain kutsuttava funktio putkittuu.

Kutsuttava funktio saa näkyvyysalueekseen sen näkyvyysalueen, jossa se on määritelty.

Argumentit annetaan funktiolle siinä järjestyksessä, missä ne ovat kutsussa. Jos funktion viimeisessä
parametrissa on valitsin `...`, asetetaan kaikki yli menevät argumentit siihen listana. Lista voi olla
myös tyhjä.

```sh
tulosta_perheenjäsenet sukunimi, etunimet... {
	for etunimi in etunimet do
		push etunimi, " ", sukunimi, "\n"
	done
}

main {
	tulosta_perheenjäsenet "Luoto", "Einari", "Ville", "Jenni"
}
```

##### *-argumentit

Jos argumentin edessä on tähti `*`, oletetaan, että se on lista. Tällöin listan alkiot annetaan
argumentteina funktiolle, eikä itse listaa.

```c
väli := [1, 10]
seq *väli /* sama kuin seq 1, 10 */
```

Tätä ominaisuutta on mahdollista käyttää yhdessä `...`-määrittimen kanssa,
jos halutaan antaa arvot olemassa olevasta listasta.

```sh
sisarukset := ["Joonas", "Amelie"]
tulosta_perheenjäsenet "Mikkola", *sisarukset
```

##### Nimetyt argumentit

Jos funktion joillakin parametreilla on oletusarvo, pitää näitä parametreja vastaavat argumentit nimetä.

```sh
korosta("kissa", väri="#2388ff")
```

##### Tyyppiargumentit

Jos funktiolle on määritelty tyyppiparametreja, sille on annettava kutsun yhteydessä vastaava määrä
tyyppiargumentteja:

```c
init_list<<string>> sisarukset
sisarukset += "Joonas"
sisarukset += "Amelie"
```

#### Listan kutsuminen

Listan "kutsuminen" työntää kaikki listan alkiot ulostulovirtaan:
```sh
["rivi1\n", "rivi2\n", "rivi3\n"] | writeStrings tiedosto
```

Listan kutsumiseen perustuu `[`- ja `]`-merkkien käyttö ehtolauseissa.

#### Muuttujat

Uuden muuttujan voi luoda operaattorilla **`:=`**:
```sh
tiedosto := "tieto.txt"
ikä := 73
tytöt := ["Annamari", "Reetta", "Vilma"]
```

Muuttujalle voi asettaa uuden arvon operaattorilla **`=`**:
```sh
ikä = 74
tytöt[1] = "Liisa"
tytöt[2:4] = ["Kaisa", "Elina"]
```

Listaan voi lisätä arvon operaattorilla **`+=`**:
```sh
tytöt += "Maija"
```

Merkkijonon perään voi lisätä tekstiä operaattorilla **`.=`**:
```sh
nimi := etunimi
nimi .= " "..sukunimi
```

Lukua voi kasvattaa tai vähentää operaattoreilla **`++`** ja **`--`**:
```sh
ikä ++
voimat --
```

Muuttujan voi tuhota käyttämällä komentoa `undefine`.
Normaalisti muuttujia ei kuitenkaan tarvitse tuhota erikseen.
```sh
undefine nimi
```
Muuttujan tuhoaminen ei poista muuttujaa varmasti, sillä se saattaa olla määritelty jollakin toisella ohjelman
tasolla.
```c
nimi := "Lissu"
{
	nimi := "Emilia"
	push nimi, "\n" /* tulostaa Emilian */
	undefine nimi
	push nimi, "\n" /* tulostaa Lissun */
}
push nimi /* tulostaa Lissun */
```
Muuttujan voi tuhota kokonaan käyttäen silmukkaa ja `?`-operaattoria, joka kertoo, onko muuttuja olemassa.
```sh
while nimi? do
	undefine nimi
done
```

Seuraavaksi vielä kaikki muuttujaoperaattorit taulukossa:

| Operaattori | Esimerkki          | Selitys                                            |
|:-----------:| ------------------ | -------------------------------------------------- |
| `:=`        | `nimi := "Liisa"`  | Luo uuden muuttujan nykyiseen muuttujaympäristöön. |
| `=`         | `nimi = "Maija"`   | Ylikirjoittaa aiemmin luodun muuttujan arvon.      |
| `?`         | `nimi?`            | Työntää ulostulovirtaan totuusarvon `true` tai `false` riippuen siitä, onko muuttuja olemassa |
| `+=`        | `tytöt += "Nea"`   | Lisää listaan elementin.                           |
| `.=`        | `tytöt .= ["Annabella", "Linn"]` | Yhdistää listaan toisen listan.       |
| `.=`        | `nimi .= sukunimi` | Lisää tekstin merkkijonon loppuun.                 |
| `~=`        | `nimi ~= "ae", "ä"` | Tekee annetut korvaukset merkkijonoon, toimii kuten funktio `replace`. |
| `+=`, `-=`, `*=`, `/=` | `pisteet *= 2` | Suorittaa laskutoimituksen lukumuuttujalla. |
| `++`, `--`  | `varallisuus --`   | Kasvattaa tai vähentää lukumuuttujan arvoa.        |
| `del`       | `del tytöt[2:4]`   | Poistaa listasta alkion tai alkioita.              |

#### Ohjausrakenteet

Ohjausrakenteita ovat `if`, `unless`, `while`, `until`, `for`, `break`, `continue`, `try` ja `return`.

##### `if`, `unless`, `while` ja `until`

`if`, `unless`, `while` ja `until` suorittavat annetun lauseen ja olettavat sen palauttavan joko arvon `true` tai arvon `false`.
Muut arvot tulkitaan aina samoin kuin `true`. Jos lause palauttaa useita arvoja, pitää niiden kaikkien olla `true`, jotta ehto toteutuisi.

Sisäänrakennetuista funktioista vain `true`, `false`, `test`, `random` ja `file` (ks. alempana) palauttavat totuusarvon.

```sh
if [ ikä < 18 ] do
	push "Olet liian nuori!\n"
done
```

```sh
while [ not ( vastaus =~ "kyllä|ei" ) ] do
	push "Vastaa kyllä tai ei: "
	pull vastaus
done
```

##### `for`

`for` käy läpi annetun listan kaikki arvot:

```sh
tytöt := [["Annamari", 1996], ["Reetta", 1992], ["Vilma", 1999]]
for tyttö in tytöt do
	push "Hänen nimensä on "..tyttö[0].." ja hän on syntynyt vuonna "..tyttö[1].."\n"
done
```

Jos `for`ille ei anna listaa, lukee se arvoja syötteestä:

```sh
["Isabella", "Meeri", "Taina"] | for tyttö do
	push tyttö.." on paikalla.\n"
done
```

`for if` -rakenteen avulla voi käydä läpi vain osan arvoista:

```sh
määrä := 0
summa := 0
for tyttö in tytöt if [ tyttö.ikä > 14 ]; do
	määrä ++
	summa += tyttö.pituus
done
push "Yli neljätoistavuotiaiden tyttöjen pituuksien keskiarvo: "..(summa/määrä).."\n"
```

##### Suffiksirakenteet

`if`ille, `while`lle ja `for`ille on olemassa myös ns. suffiksimuoto:

```sh
push tyttö.nimi.." on paikalla.\n" for tyttö in tytöt
push tyttö.nimi.." ei ole kiireinen.\n" for tyttö in tytöt if [ tyttö.kiire = 0 ]

hinta /= 2 if push alennus

tyttö = haeSeuraava() while tarkista tyttö
```

Suffiksit ovat laskujärjestyksessä korkeammalla kuin putket. Sulkuja `{}` voi käyttää tämän kiertämiseksi:

```sh
haeViestit() | split(:s, "\\b") | { haeTytöt | push tyttö for tyttö if [ tyttö.nimi = sana ] } for sana | for tyttö do
	push tyttö.." mainittiin keskustelussa.\n"
done
```

##### `break` ja `continue`

`break`ia ja `continue`a voi käyttää silmukasta poistumiseen tai vuoron yli hyppäämiseen.

##### `try`

`try` suorittaa annetun komennon tai lohkon ja ohittaa hiljaisesti kaikki vastaan tulleet virheet.

```sh
while true do
	try do
		hae viestit
		käsittele viestit
	done
done
```

Suorituksen keskeyttänyt virhe asetetaan `catch`-osion muuttujaan, mikäli se on määritelty.

```sh
try do
	lähetäViesti
catch virhe
	errprint "Viestiä ei voitu lähettää!"
	errprint virhe.message
	errprint kohta for kohta in virhe.stack
done
```

##### `return`

`return` työntää sille annetut argumentit ulostulovirtaan ja lopettaa nykyisen funktion suorittamisen.

```sh
haeSyntymävuodellaYksiTyttö vuosi {
	for tyttö in tytöt do
		if [ tyttö[1] = vuosi ] do
			return tyttö
		done
	done
}
```

### Lausekkeet

Funktion argumentit (ja kutsuttava funktio itsekin) ovat lausekkeita. Lauseke voi olla joko muuttuja,
luku, merkkijono, lista, komento tai nimetön funktio. Lisäksi Rödassa on muutama operaattori
merkkijonojen ja listojen käsittelemiseen.

#### Luvut ja merkkijonot

Kaikki funktiot hyväksyvät lukujen tilalla merkkijonoja (joiden toki pitää sisältää vain lukuja)
ja merkkijonojen tilalla lukuja. Optimointisyistä on kuitenkin aina hyvä käyttää lukuliteraaleja kaikkialla,
missä mahdollista.

Merkkijonoja voi yhdistellä `..`-operaattorilla ja niiden pituuden voi saada `#`-operaattorilla.

```sh
nimi := etunimi.." "..sukunimi
push "Nimesi pituus on ", #nimi, "\n"
```

#### Listat

Listaliteraali on joukko hakasulkeiden ympäröimiä arvoja, jotka erotellaan pilkuilla.

Listasta voi hakea yksittäisiä alkioita `[]`-operaattorilla. Lisäksi osalistoja voi luota `[:]`-operaattorilla.
Kuten merkkijonoillakin, `#` palauttaa listan koon.

Kaikki listan alkiot voi yhdistää merkkijonoksi `&`-operaattorilla, jonka toinen operandi on merkkijono, joka
pistetään alkioiden väleihin.

```sh
tytöt := ["Annamari", "Reetta", "Vilma", "Susanna"]
push "Tyttöjä on ", #tytöt, " kpl.\n"
push "Ensimmäinen tyttö on ", tytöt[0], " ja viimeinen ", tytöt[-1], ". "
push "Välissä ovat ", tytöt[1:-1]&" ja ", ".\n"
```

Jos listaan yhdistää merkkijonon, yhdistetään se kaikkiin listan alkioihin:

```sh
sukunimi := "Kivinen"
sisarukset := ["Maija", "Ilmari"]
kokonimet := sisarukset.." "..sukunimi
push "Sisarusten koko nimet ovat ", kokonimet&" ja ", ".\n"
```

Listan alkioille voi määritellä tyypin, jos se luodaan `new`-avainsanan avulla:

```sh
tytöt := new list<<string>>
tytöt .= ["Eveliina", "Lilja", "Nea"]
```

Jos listaan yrittäisi laittaa joitain muita olioita kuin merkkijonoja, antaisi koodi suorituksenaikaisen
virheen.

#### Kartat

Uuden kartan voi luoda samaan tapaan kuten tietueolion.
Kuten listoille, myös tauluille voi määritellä erikseen alkion tyypin. Tätä ei kuitenkaan ole pakko tehdä.

```sh
iät := new map<<number>>
iät["Maija"] = 13
iät["Ilmari"] = 19
```

`?`-operaattorilla voi tarkastaa, onko kartassa tietty alkio:

```sh
unless [ iät["Maija"]? ] do
	push "Maijan ikää ei löydy!\n"
done
```

#### Operaattorit

Operaattorit taulukossa (tunniste tarkoittaa joko lukua tai merkkijonoa riippuen siitä, onko kyseessä lista
vai kartta):


| Operaattori | Selitys                     | Ottaa             | Palauttaa     |
|:-----------:| --------------------------- | ----------------- | ------------- |
| `..`        | Yhdistää merkkijonoja       | 2 arvoa, merkkijonoja tai listoja | Merkkijonon tai listan |
| `&`         | Yhdistää listan alkiot merkkijonoksi | Listan ja merkkijonon    | Merkkijonon            |
| `#`         | Palauttaa arvon pituuden    | Listan, kartan tai merkkijonon    | Kokonaisluvun          |
| `[]`        | Palauttaa listan alkion     | Listan tai kartan ja tunnisteen   | Alkion                 |
| `[:]`       | Palauttaa listan osalistan  | Listan tai merkkijonon ja nollasta kahteen kokonaislukua | Listan tai merkkijonon |
| `[]?`       | Kertoo, onko alkio olemassa | Listan tai kartan ja tunnisteen   | Totuusarvon            |
| `in`        | Kertoo, onko listassa arvo  | Minkä tahansa arvon               | Totuusarvon            |
| `is`        | Kertoo, onko arvo tiettyä tyyppiä | Minkä tahansa arvon ja tyypin | Totuusarvon          |
| `and`       | Looginen JA                 | 2 totuusarvoa     | Totuusarvon   |
| `or`        | Looginen TAI                | 2 totuusarvoa     | Totuusarvon   |
| `xor`       | Looginen JOKO-TAI           | 2 totuusarvoa     | Totuusarvon   |
| `=`         | Yhtäsuuruus                 | Mitä tahansa      | Totuusarvon   |
| `!=`        | Erisuuruus                  | Mitä tahansa      | Totuusarvon   |
| `<`         | Pienempi kuin               | 2 lukua           | Totuusarvon   |
| `>`         | Suurempi kuin               | 2 lukua           | Totuusarvon   |
| `<=`        | Pienempi tai yhtäsuuri kuin | 2 lukua           | Totuusarvon   |
| `>=`        | Suurempi tai yhtäsuuri kuin | 2 lukua           | Totuusarvon   |
| `b_and`     | Bittitason JA               | 2 kokonaislukua   | Kokonaisluvun |
| `b_or`      | Bittitason TAI              | 2 kokonaislukua   | Kokonaisluvun |
| `b_xor`     | Bittitason JOKO-TAI         | 2 kokonaislukua   | Kokonaisluvun |
| `b_shiftl`  | Bittitason vasen siirto     | 2 kokonaislukua   | Kokonaisluvun |
| `b_shiftr`  | Bittitason oikea siirto     | 2 kokonaislukua   | Kokonaisluvun |
| `b_shiftrr` | Bittitason etumerkitön oikea siirto | 2 kokonaislukua | Kokonaisluvun |
| `+`         | Yhteenlasku                 | 2 lukua           | Luvun         |
| `-`         | Vähennyslasku               | 2 lukua           | Luvun         |
| `*`         | Kertolasku                  | 2 lukua           | Luvun         |
| `/`         | Jakolasku                   | 2 lukua           | Liukuluvun    |
| `//`        | Pyöristävä jakolasku        | 2 lukua           | Kokonaisluvun |
| `%`         | Jakojäännös                 | 2 kokonaislukua   | Kokonaisluvun |
| Unäärinen `-` | Vastaluku                 | Luvun             | Luvun         |
| Unäärinen `b_not` | Bittitason EI         | Kokonaisluvun     | Kokonaisluvun |
| Unäärinen `not` | Looginen EI             | Totuusarvon       | Totuusarvon   |

Laskujärjestys:

| Sija | Operaattorit                                               |
|:----:| ---------------------------------------------------------- |
| 1.   | `[]`, `[:]`, `[]?`, `is`                                   |
| 2.   | Unäärinen `-`, unäärinen `~`, unäärinen `!`, unäärinen `#` |
| 3.   | `*`, `//`, `%`                                             |
| 4.   | `+`, binäärinen `-`                                        |
| 5.   | `b_and`, `b_or`, `b_xor`, `b_shiftl`, `b_shiftr`, `b_shiftrr` |
| 6.   | `<`, `>`, `<=`, `>=`, `in`                                 |
| 7.   | `&`                                                         |
| 8.   | `..`                                                       |
| 9.   | `=`, `!=`                                                  |
| 10.  | `and`, `or`, `xor`                                        |

#### Komento

Lauseke voi olla myös komento tai putkitettuja komentoja. Tälloin lausekkeen arvoksi tulee lista,
joka on muodostettu kaikista viimeisen komennon ulostulon antamista arvoista.

Seuraava ohjelma tulostaa tiedoston rivinumeroiden kera.

```sh
rivit := [readLines(tiedosto)]
i := 1
for rivi in rivit do
	push i, " ", rivi, "\n"
	i ++
done
```

Jos on varmaa, että funktio antaa vain yhden arvon, voi hakasulkeet jättää pois. Tällöin arvoksi
tulee listan ainoa arvo. Tämä heittää virheen, jos funktio palauttaa useampia arvoja (tai ei yhtään).

```sh
kaksi := parseInteger("2")
```

#### Nimetön funktio

Nimetön funktio toimii kuten tavallinenkin funktio. Syntaksi on `{ |parametrit|; koodi }`.

Seuraavassa koodissa määritellään `filter`-funktio, joka lukee arvoja ja palauttaa osan niistä.

```sh
filter condFunction {
	for value do
		if condFunction value do
			push value
		done
	done
}
```

Funktiota käytetään antamalla sille nimetön funktio (tai tavallinenkin funktio käy), joka palauttaa
`true`n tai `false`n.

```sh
tytöt := [["Annamari", 1996], ["Reetta", 1992], ["Vilma", 1999]]
tytöt | filter { |tyttö|; [ tyttö[1] > 1995 ] } | for tyttö do
	push tyttö[0], " on vielä nuori.\n"
done
```

#### Reflektio

Reflektion avulla on mahdollista saada metatietoa olioiden tyypeistä. Rödassa on kaksi mekanismia reflektion
käyttämiseen: `reflect`-avainsana ja `typeof`-avainsana.

`reflect` palauttaa annetun tyypin metaluokan, joka on tyyppiä `Type`.
```c
record R {
	a : string
	b : number
}

...

(reflect R).fields /* palauttaa listan, jossa on kaksi field-oliota, yksi a:lle ja toinen b:lle */
```

`typeof` toimii samoin, mutta ottaa tyyppinimen sijasta arvon ja palauttaa sen tyypin.

## Esimerkkejä

```sh
push ENV["PATH"] | split sep=":" | ls dir for dir | createGlobal komento, { |a...|; exec komento, *a } for komento
```

Etsii kaikki komentorivikomennot ja tekee jokaisesta funktion. Tämän jälkeen komentoja voi käyttää suoraan ilman
`exec`iä.

## Lista sisäänrakennetuista tietueluokista

Näiden lisäksi jotkin funktiot palauttavat omia tietueitaan, joiden määritykset listattu kyseisten funktioiden kohdalla.

### Error

```
record Error {
	message : string
	stack : list<<string>>
	javastack : list<<string>>
}
```

### Type ja Field

```
record Type {
	name : string
	annotations : list
	fields : list<<Field>>
	newInstance : function
}

record Field {
	name : string
	annotations : list
	type : Type
	get : function
	set : function
}
```

## Linkkejä

Sisäänrakennetut funktiot ja tyypit on dokumentoitu [täällä](http://kaivos.org/~iikka/röda/doc/).
