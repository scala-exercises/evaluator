[![Build Status](https://travis-ci.org/scala-exercises/evaluator.svg?branch=master)](https://travis-ci.org/scala-exercises/evaluator)

# Remote Scala Eval

The remote Scala evaluator is a server based application that
allows remote evaluation of arbitrary Scala code.

# Run from sources

```bash
sbt "project evaluator-server" "run"
```

# Authentication

The remote Scala eval uses [JWT](https://jwt.io/) to encode / decode tokens.
The `secretKey` used for encoding/decoding is configurable as part of the service configuration in 
`server/src/main/resources/application.conf`.

Please change `secretKey` by overriding it or providing the `EVAL_SECRET_KEY` env var.

```
eval.auth {
  secretKey = "secretKey"	  
  secretKey = ${?EVAL_SECRET_KEY}
}
```

## Generate an auth token

In order to generate an auth token you may use the scala console and invoke
the `org.scalaexercises.evaluator.auth#generateToken` like so

```
sbt "project evaluator-server" "console"

scala> import org.scalaexercises.evaluator.auth._

scala> generateToken("your identity")
res0: String = eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eW91ciBpZGVudGl0eQ.cfH43Wa7k_w1i0W2pQhV1k21t2JqER9lw5EpJcENRMI
```

Note `your identity` is exclusively to identify incoming requests for logging purposes.
The Scala evaluator will authorize any incoming request generated with the `secretKey`

# Request

Requests are sent in JSON format via HTTP POST and are authenticated via the `x-scala-eval-api-token` header.

# Sample Request

Given the token above a sample request may look like:

```bash
curl -X POST -H "Content-Type: application/json" -H "x-scala-eval-api-token: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eW91ciBpZGVudGl0eQ.cfH43Wa7k_w1i0W2pQhV1k21t2JqER9lw5EpJcENRMI" -d '{  
   "resolvers":[  
      "https://oss.sonatype.org/content/repositories/releases"
   ],
   "dependencies":[  
      {  
         "groupId":"org.typelevel",
         "artifactId":"cats-core_2.11",
         "version":"0.4.1"
      }
   ],
   "code":"{import cats._; Monad[Id].pure(42)}"
}
' "http://localhost:8080/eval"
```

## Headers

```
Content-Type : application/json
x-scala-eval-api-token : eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eW91ciBpZGVudGl0eQ.cfH43Wa7k_w1i0W2pQhV1k21t2JqER9lw5EpJcENRMI 
```

## Body

```json
{  
   "resolvers":[  
      "https://oss.sonatype.org/content/repositories/releases"
   ],
   "dependencies":[  
      {  
         "groupId":"org.typelevel",
         "artifactId":"cats-core_2.11",
         "version":"0.4.1"
      }
   ],
   "code":"{import cats._; Monad[Id].pure(42)}"
}
```

- `resolvers` : A list of resolvers where artifacts dependencies are hosted
- `dependencies` : A List of artifacts required to eval the code
- `code` : Some Scala Code

## Response

After compiling and attempting to evaluate the server will return a response payload with the following fields:

- `msg` : A message indicating the result of the compilation and evaluation. Note failing to compile still yields http status 200 as the purpose of the service is to output a result without judging if the input was correct or not.	
- `value` : The result of the evaluation or `null` if it didn't compile or an exception is thrown.
- `valueType` : The type of result or `null` if it didn't compile or an exception is thrown
- `compilationInfos` : A map of compilation severity errors and their associated message

For ilustration purposes here is a few Response Payload examples:

Successful compilation and Evaluation

```json
{
  "msg": "Ok",
  "value": "42",
  "valueType": "java.lang.Integer",
  "compilationInfos": {}
}
```

Compilation Failure

```json
{
  "msg": "Compilation Error",
  "value": null,
  "valueType": null,
  "compilationInfos": {
    "ERROR": [
      {
        "message": "value x is not a member of cats.Monad[cats.Id]",
        "pos": {
          "start": 165,
          "point": 165,
          "end": 165
        }
      }
    ]
  }
}
```

Evaluating code that may result in a thrown exception

```json
{
  "msg": "Runtime Error",
  "value": null,
  "valueType": null,
  "compilationInfos": {}
}
```

# License

Copyright (C) 2015-2016 47 Degrees, LLC.
Reactive, scalable software solutions.
http://47deg.com
hello@47deg.com

Some parts of the code have been taken from [twitter-eval](https://github.com/twitter/util/tree/302235a473d20735e5327d785e19b0f489b4a59f/util-eval), and slightly adapted to the evaluator needs. Copyright 2010 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
