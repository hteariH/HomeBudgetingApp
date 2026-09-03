// Dev loop only: `wasmJsBrowserDevelopmentRun` serves the bundle from its own port, while
// the API lives on the Spring server at 8085. The client asks its own origin for /api
// (see WebModules.kt), so without this the dev server answers 404 and the app looks broken
// in a way that has nothing to do with the code being worked on.
//
// A proxy rather than CORS on the server: in production the same Spring app serves both the
// bundle and the API, so there is no cross-origin request to allow, and adding a CORS
// configuration for the dev loop alone would be a production-visible setting existing purely
// for development. This is the same trick the React client uses with "proxy" in its
// package.json.
//
// Kotlin's plugin merges every .js in this directory into the generated webpack config, so
// this survives plugin upgrades that would rename the Gradle DSL types.
if (config.devServer) {
    // Array form: webpack-dev-server takes an object in v4 and an array from v5 on, and the
    // array is accepted by both.
    config.devServer.proxy = [
        {
            context: ['/api'],
            target: 'http://localhost:8085',
            changeOrigin: false,
        },
    ];
}
