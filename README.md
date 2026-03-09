This is an application for having participants interact with an AI agent investigator. It is
intended for research purposes.

### Browser (for Kotlin/Wasm target)

**Chrome and Chromium-based**

* **For version 119 or later:**

  Works by default.

**Firefox**

* **For version 120 or later:**

  Works by default.

**Safari/WebKit**

Wasm GC support is currently under
[active development](https://bugs.webkit.org/show_bug.cgi?id=247394).

> **Note:**
> For more information about the browser versions, see the [Troubleshooting documentation](https://kotl.in/wasm_help/).

## Build and run

Run the application by typing one of the following Gradle commands in the terminal:

* **Web version:**

  `./gradlew :composeApp:wasmJsBrowserProductionRun`
  <br>&nbsp;<br>

  Once the application starts, open the following URL in your browser:

  `http://localhost:8080`

  > **Note:**
  > The port number can vary. If the port 8080 is unavailable, you can find the corresponding port number printed in the console
  > after building the application.
<br>&nbsp;<br>

* **Desktop version:**

  `./gradlew :composeApp:run`
