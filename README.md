[![Build Status](https://travis-ci.org/thomasylee/Yojik.svg?branch=master)](https://travis-ci.org/thomasylee/Yojik)

# Yojik
Definitions:
* A friendly nocturnal mammal with small spines on its back (see [ёжик](https://ru.wikipedia.org/wiki/%D0%9E%D0%B1%D1%8B%D0%BA%D0%BD%D0%BE%D0%B2%D0%B5%D0%BD%D0%BD%D1%8B%D0%B9_%D1%91%D0%B6)).
* An awesome open source XMPP server written in Scala!

## Features

Currently, Yojik only supports the following features:
* Receiving a TCP connection on port 5222
* Opening an XML stream as defined in [XMPP Core](https://xmpp.org/rfcs/rfc6120.html)
* Securing the connection with STARTTLS as a required stream feature
* Reopening the XML stream over the encrypted connection
* SASL-PLAIN authentication

# Building

`sbt compile`

# Testing

`sbt test`

# Running

Yojik listens to port 5222 on localhost.

`sbt run`
