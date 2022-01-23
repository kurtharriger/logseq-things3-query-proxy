# Logseq Query Service for Things 3 Database

Simple HTTP service to enable logseq Things 3 plugin (under development) running in sandbox to query Things 3 sqlite database

For now I just run in terminal before to query endpoint and the endpoint simply dumps all data I care about.  


## How to Run
```
npm install

npm run dev
npx electron .
```

## Release
```
npm run build
npx electron-packager . HelloWorld --platform=darwin --arch=x64
```


## References

[Obsidian Things Logbook](https://github.com/liamcain/obsidian-things-logbook)
[things.sh](https://github.com/AlexanderWillner/things.sh)


