# Mill Remote Cache Server

Just a simple remote caching server for now.
Supports get and put requests for anything hashable in the mill out cache.
References in meta.json are expected to be relative.

The mill tool has Tasks and some of the outputs have a hash associated with it.

## WIP Endpoints

```
PUT /cache/[pathFromOut]/[hash]
    Attach zip
GET /cache/[pathFromOut]/[hash]
    Responds with zip
GET /allCached
    Responds with JSON of all pathFromOut and hashes
```

## Example

A simple scala module that prints HelloWorld may have the following structure.

```
out/
    helloWorld/
        allSources/
            meta.json (has reference to source and an input hash 1)
        ...
        compile/
            dest/classes/HelloWorld.class
            meta.json (has reference to above .class file and an input hash 2)
        ...
```

The first time we run mill locally we attempt to call
`GET /allCached` and receive an empty response since nothing has been cached yet.


After mill finishes we zip `compile` and `allSources` because they both contain an input hash as shown above.
Then the following is uploaded `PUT /cache/allSources/1` and `PUT /cache/compile/2`.

S3 will have a similar structure to out but anything hashable will be used as a directory.

```
out/
    helloWorld/
        allSources/
            1/
                meta.json (has reference to source and an input hash 1)
        ...
        compile/
            2/
                dest/classes/HelloWorld.class
                meta.json (has reference to above .class file and an input hash 2)
        ...
```

When another person checks out your project and runs `mill helloWorld` mill will call `GET /allCached`
and receive something like `[{pathFromOut: "helloWorld/allSources", hash: 1}, {pathFromOut: "helloWorld/compile", hash: 2}]`
and then when mill sees that it needs to create helloWorld/allSources and it calculates an input hash of 1.
Then it will see that it is in the remote cache and call `GET /cache/allSources/1` move the contents to `out/helloWorld/allSources/`.

