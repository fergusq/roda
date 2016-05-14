# A guide to Röda programming

## Prime generator explained

In this section we're going to review some syntactic features of Röda which may be confusing to beginners.
In README.md, I used this prime generator as an example:

```sh
primes := (2)
seq 3 10000 | { primes += i if [ i % p != 0 ] for p in primes } for i
print p for p in primes
```

What does that program do? If you execute it, it will pause for a moment and then print the first prime numbers up to 9973.
It makes a heavy use of a syntactic sugar called _suffix control structures_. They are a shorter way to write loops and conditionals.

Let's look at the last line first. It takes the `primes` array and prints its content, each number to its own line.
It consists of the statement, `print p`, and the suffix, `for p in primes`. The only difference to a normal loop is that the
body is written _before_ the loop declaration.

But how does the second line work? I have written the loops and conditionals without suffix syntax below:

```sh
seq 3 10000 | for i do
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
Still, they can be handy sometimes: they provide a more natural syntax to do many things like list comprehension and looped conditions.
It is advisable not to nest these more than two levels, thought.
