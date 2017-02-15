# A guide to Röda programming

## The basics

### Hello world -example

```sh
main {
	print "Hello world!"
}
```

Every Röda program consists of a list of function declarations.
When the program is run, the interpreter executes the `main` function (defined by lines 1 and 3).
At line 2 there is the body of the function that happens to be a single function call.

For the rest of this guide I will omit the signature of the main program.

### Commands and variables

In Röda statements are also called _commands_. We have already seen a function call, which is the most used command type,
but there are also many others like `if`, `while` and `for`.

The maybe second most used command is variable assignment. Below is a basic example of that.

```sh
name := "Caroline"
age := 31
print name, ", ", age, " years"
age = 32
print name, ", ", age, " years"
```

Here we declare two variables, `name` and `age` and use them to print an introductive text of a person: `Caroline, 31 years`.
Then we change the age variable and print to same text again. In Röda `:=` is used to create a new variable and `=` to edit an old.

Using variables and a while loop we can print the first even numbers:

```sh
i := 0
while [ i < 10 ] do
	print i*2
	i++
done
```

Like in Bourne shell, condition is a command. It can be an arithmetic command `[]` or a normal function call like below.
`fileExists` is used to check if the file exists.

```sh
if fileExists "log.txt" do
	{} | exec "rm", "log.txt" | {}
done
```

Unlike Bourne shell, `if` statements have `do` and `done`, not `then` and `fi`.

## Prime generator explained

In this section we're going to review some syntactic features of Röda which may be confusing to beginners.
In README.md, I used this prime generator as an example:

```sh
primes := [2]
seq 3, 10000 | { primes += i if [ i % p != 0 ] for p in primes } for i
print p for p in primes
```

What does that program do? If you execute it, it will pause for a moment and then print the first prime numbers up to 9973.
It makes a heavy use of a syntactic sugar called _suffix control structures_. They are a shorter way to write loops and conditionals.

Let's look at the last line first. It takes the `primes` array and prints its content, each number to its own line.
It consists of the statement, `print p`, and the suffix, `for p in primes`. The only difference to a normal loop is that the
body is written _before_ the loop declaration.

But how does the second line work? I have written the loops and conditionals without suffix syntax below:

```sh
seq 3, 10000 | for i do
	if for p in primes do [ i % p != 0 ] done do
		primes += i
	done
done
```

As you can see, it is a basic `seq for`-loop that iterates numbers from 3 to 10000.
It then uses an if statement to determinate if the number is a prime, but how can there be a loop inside the condition?
It happens that in Röda conditions are just statements and the value of the condition is just taken from the output stream of the statement.
In this case the loop pushes more than one boolean to the stream and the body of the condition is executed when _all of them_ are true.
The condition can in other words be read _a number is a prime if none of the previous primes divide it_.

So are these suffix commands really required? It seems that one must read the code backwards to understand them!
Still, they can be handy sometimes: they provide a more natural syntax to do many things like list comprehension and looped conditions (see below).
It is advisable not to nest these more than two levels, though.

## Syntactic features

### Suffix commands

The suffix syntax is a generalization of many other features like list comprehension, `map`- and `filter`-functions, looped conditionals, etc.

#### List comprehension

```sh
new_list := [statement for variable in list if statement]
```

Examples:
```sh
names := [get_name(client) for client in clients]
adults := [push(client) for client in clients if [ client.age >= 18 ]]
pwdir := pwd()
directories := [push(pwdir.."/"..f) for f in files if isDirectory(f)]
```

#### Mapping and filtering

`for` can also used in pipes. `if` and `unless` are right-associative, so braces `{}` should be used around them.

```ruby
["Pictures/", "Music/"] | bufferedExec("ls", dir) for dir | { [ f ] for f unless isDirectory(f) } | for f do
	print f.." "..mimeType(f)
done
```

#### Looped conditionals

```sh
if [ condition ] for element in list do

done
```

Ensures that the condition is true for all elements in list.

```sh
if isDirectory(f) for f in [ls(".")] do
	print "All files are directories!"
done
```