# Mill Remote Cache Server

Just a simple remote caching server for now used as a proposal.
Supports get and put requests for anything hashable in the mill out cache.
References in meta.json are expected to be relative.

## WIP Endpoints

```
PUT /cached?path=[pathFromOut]?hash=[hash]
    Attach zip
GET /cached/?path=[pathFromOut]?hash=[hash]
    Responds with zip
GET /cached
    Responds with JSON of all pathFromOut and hashes
```

## Example

The mill tool has Tasks and some of the outputs have a hash associated with it.
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
`GET /cached` and receive an empty response since nothing has been cached yet.


After mill finishes we zip `compile` and `allSources` because they both contain an input hash as shown above.
Then the following is uploaded `PUT /cache/allSources?hash=1` and `PUT /cache/compile?hash=2`.

The caching server could handle storage using different providers such as S3 or Google Cloud Storage. This is one prototype of a caching server.

## Implementation
So far I have an S3 based implementation that a similar structure to out but anything hashable will be used as a key.
Hashes are base32 encoded to handle negative hashes which would otherwise be invalid directories.

```
helloWorld/allSources/{Base32 of 1} => tar.gz of allSources with meta.json containing relative paths
    ...
helloWorld/compile/{Base32 of 2} => tar.gz of compile with meta.json containing relative paths.
    ...
```

When another person checks out your project and runs `mill helloWorld` mill will call `GET /cached`
and receive something like `[{pathFromOut: "helloWorld/allSources", hash: 1}, {pathFromOut: "helloWorld/compile", hash: 2}]`
and then when mill sees that it needs to create helloWorld/allSources and it calculates an input hash of 1.
Then it will see that it is in the remote cache and call `GET /cache/allSources?hash=1` move the contents to `out/helloWorld/allSources/`.

