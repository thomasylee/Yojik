[![Build Status](https://travis-ci.org/thomasylee/yojik.svg?branch=master)](https://travis-ci.org/thomasylee/yojik)
[![Codacy Coverage Badge](https://api.codacy.com/project/badge/Coverage/b74a3556b4394a24b5eb49309a3ede48)](https://www.codacy.com/app/thomasylee/Yojik?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=thomasylee/yojik&amp;utm_campaign=Badge_Coverage)
[![Codacy Grade Badge](https://api.codacy.com/project/badge/Grade/b74a3556b4394a24b5eb49309a3ede48)](https://www.codacy.com/app/thomasylee/Yojik?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=thomasylee/yojik&amp;utm_campaign=Badge_Grade)

# Yojik
Definitions:
* A friendly nocturnal mammal with small spines on its back (see [ёжик](https://ru.wikipedia.org/wiki/%D0%9E%D0%B1%D1%8B%D0%BA%D0%BD%D0%BE%D0%B2%D0%B5%D0%BD%D0%BD%D1%8B%D0%B9_%D1%91%D0%B6)).
* An awesome open source XMPP server written in Scala!

## Features

Yojik aims to fulfill all/most of the MUST requirements in RFC-6120 and will eventually be extensible enough to support additional XEPs and other features.

Currently, Yojik only supports the following features:
* Receiving a TCP connection on port 5222
* Opening an XML stream as defined in [XMPP Core](https://xmpp.org/rfcs/rfc6120.html)
* Securing the connection with STARTTLS as a required stream feature
* Reopening the XML stream over the encrypted connection
* SASL-PLAIN authentication
* Resource binding

# Building

`sbt compile`

# Testing

`sbt test`

# Running

Yojik listens to port 5222 on localhost.

`sbt run`
