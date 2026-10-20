package ai.sotuvchi.app

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * The mock API's test hooks (the /mock paths in Tools/mock-server/mock_server.py).
 * The emulator reaches the Mac's mock through `adb reverse tcp:8787 tcp:8787`.
 */
object Mock {
    const val ROOT = "http://localhost:8787"
    const val API_URL = "$ROOT/api"

    class Failure(message: String) : Exception(message)

    /** Ends the mock session and forgets codes and new accounts, so the app starts signed out. */
    fun reset(failDelete: Boolean = false, soloOwner: Boolean = false) {
        post("mock/reset", JSONObject().put("failDelete", failDelete).put("soloOwner", soloOwner))
    }

    fun failDelete(fail: Boolean) {
        post("mock/fail-delete", JSONObject().put("fail", fail))
    }

    /** The code the mock "emailed", once the app has asked for it. */
    fun code(email: String): String {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val (status, body) = request("GET", "mock/code?email=" + URLEncoder.encode(email, "UTF-8"), null)
            if (status == 200) {
                JSONObject(body).optJSONObject("data")?.optString("code")?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            Thread.sleep(300)
        }
        throw Failure("The mock never sent a code to $email")
    }

    /** Waits until a browser has used a web sign-in link for [path]. */
    fun waitForHandoff(path: String, timeoutMs: Long = 20_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val (_, body) = request("GET", "mock/handoffs", null)
            val handoffs = JSONObject(body).optJSONArray("data")
            if (handoffs != null) {
                for (i in 0 until handoffs.length()) {
                    val entry = handoffs.getJSONObject(i)
                    if (entry.optString("path") == path && entry.optBoolean("redeemed")) return true
                }
            }
            Thread.sleep(300)
        }
        return false
    }

    /** The handoff links asked for so far, whether or not a browser used them. */
    fun handoffs(): List<Pair<String, Boolean>> {
        val (_, body) = request("GET", "mock/handoffs", null)
        val array = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it).let { e -> e.optString("path") to e.optBoolean("redeemed") } }
    }

    private fun post(path: String, body: JSONObject) {
        val (status, _) = request("POST", path, body.toString())
        if (status != 200) throw Failure("The mock answered $path with $status. Is it up to date?")
    }

    private fun request(method: String, path: String, body: String?): Pair<Int, String> {
        try {
            val connection = URL("$ROOT/$path").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val text = (if (status < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            return status to text
        } catch (error: java.io.IOException) {
            throw Failure("The mock API is not running on $ROOT: ${error.message}. Run `make ui-test`.")
        }
    }
}
