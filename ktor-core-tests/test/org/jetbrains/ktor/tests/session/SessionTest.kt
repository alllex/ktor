package org.jetbrains.ktor.tests.session

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.util.*
import kotlin.test.*

class SessionTest {
    val cookieName = "_S" + Random().nextInt(100)

    @Test
    fun testSessionByValue() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName)
            }

            application.routing {
                get("/0") {
                    assertNull(call.sessions.get<TestUserSession>())
                    call.respondText("No session")
                }
                get("/1") {
                    var session: TestUserSession? = call.sessions.get()
                    assertNull(session)

                    assertFailsWith(IllegalArgumentException::class) {
                        call.sessions.set(EmptySession()) // bad class
                    }

                    call.sessions.set(TestUserSession("id1", emptyList()))
                    session = call.sessions.get()
                    assertNotNull(session)

                    call.respondText("ok")
                }
                get("/2") {
                    assertEquals(TestUserSession("id1", emptyList()), call.sessions.get<TestUserSession>())

                    call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                }
            }

            handleRequest(HttpMethod.Get, "/0").let { call ->
                assertNull(call.response.cookies[cookieName], "There should be no session set by default")
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(TestUserSession("id1", emptyList()), autoSerializerOf<TestUserSession>().deserialize(sessionParam))
            }
            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }
        }
    }

    @Test
    fun testSessionByValueDigest() {
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName) {
                    transform(SessionTransportTransformerDigest())
                }
            }

            application.routing {
                get("/1") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/2") {
                    call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                }
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionId)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                //                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionId")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(brokenSession)}")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }
        }
    }

    @Test
    fun testSessionByValueMac() {
        val key = hex("03515606058610610561058")
        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName) {
                    transform(SessionTransportTransformerMessageAuthentication(key))
                }
            }

            application.routing {
                get("/1") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/2") {
                    call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                }
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionId)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                //                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionId")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(brokenSession)}")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }
        }
    }

    @Test
    fun testRoutes() {
        withTestApplication {
            application.routing {
                route("/") {
                    install(Sessions) {
                        cookie<TestUserSession>(cookieName)
                    }

                    get("/0") {
                        assertNull(call.sessions.get<TestUserSession>())
                        call.respondText("No session")
                    }
                    get("/1") {
                        var session: TestUserSession? = call.sessions.get()
                        assertNull(session)

                        assertFailsWith(IllegalArgumentException::class) {
                            call.sessions.set(EmptySession()) // bad class
                        }

                        call.sessions.set(TestUserSession("id1", emptyList()))
                        session = call.sessions.get()
                        assertNotNull(session)

                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(TestUserSession("id1", emptyList()), call.sessions.get<TestUserSession>())

                        call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/0").let { call ->
                assertNull(call.response.cookies[cookieName], "There should be no session set by default")
                assertEquals("No session", call.response.content)
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(TestUserSession("id1", emptyList()), autoSerializerOf<TestUserSession>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }
        }
    }

    @Test
    fun testRoutesIsolation() {
        withTestApplication {
            val sessionA = TestUserSession("id1", listOf("a"))
            val sessionB = TestUserSessionB("id2", listOf("b"))
            application.routing {
                route("/a") {
                    install(Sessions) {
                        cookie<TestUserSession>(cookieName)
                    }

                    get("/1") {
                        call.sessions.set(sessionA)
                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(sessionA, call.sessions.get<TestUserSession>())
                        call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                    }
                }

                route("/b") {
                    install(Sessions) {
                        cookie<TestUserSessionB>(cookieName)
                    }
                    get("/1") {
                        call.sessions.set(sessionB)
                        call.respondText("ok")
                    }
                    get("/2") {
                        assertEquals(sessionB, call.sessions.get<TestUserSessionB>())
                        call.respondText("ok, ${call.sessions.get<TestUserSessionB>()?.userId}")
                    }
                }
            }

            var sessionParam: String = ""
            handleRequest(HttpMethod.Get, "/a/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(sessionA, autoSerializerOf<TestUserSession>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/a/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id1", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/b/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionParam = sessionCookie!!.value

                assertEquals(sessionB, autoSerializerOf<TestUserSessionB>().deserialize(sessionParam))
                assertEquals("ok", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/b/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionParam)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }
        }
    }

    @Test
    fun testSessionById() {
        val sessionStorage = SessionStorageMemory()

        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage)
            }

            application.routing {
                get("/0") {
                    call.respondText("It should be no session started")
                }
                get("/1") {
                    call.sessions.set(TestUserSession("id2", listOf("item1")))
                    call.respondText("ok")
                }
                get("/2") {
                    val session = call.sessions.get<TestUserSession>()
                    assertEquals("id2", session?.userId)
                    assertEquals(listOf("item1"), session?.cart)

                    call.respondText("ok")
                }
                get("/3") {
                    call.respondText(call.sessions.get<TestUserSession>()?.userId ?: "no session")
                }
            }

            handleRequest(HttpMethod.Get, "/0").let { response ->
                assertNull(response.response.cookies[cookieName], "It should be no session set by default")
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { response ->
                val sessionCookie = response.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session id cookie found")
                sessionId = sessionCookie!!.value
                assertTrue { sessionId.matches("[A-Za-z0-9]+".toRegex()) }
            }
            val serializedSession = runBlocking {
                sessionStorage.read(sessionId) { it.toInputStream().reader().readText() }
            }
            assertNotNull(serializedSession)
            assertEquals("id2", autoSerializerOf<TestUserSession>().deserialize(serializedSession).userId)

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionId")
            }

            handleRequest(HttpMethod.Get, "/3") {
                addHeader(HttpHeaders.Cookie, "$cookieName=bad$sessionId")
            }.let { call ->
                assertEquals("no session", call.response.content)
            }
        }
    }

    @Test
    fun testSessionByIdDigest() {
        val sessionStorage = SessionStorageMemory()

        withTestApplication {
            application.install(Sessions) {
                cookie<TestUserSession>(cookieName, sessionStorage) {
                    transform(SessionTransportTransformerDigest())
                }
            }

            application.routing {
                get("/1") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.respondText("ok")
                }
                get("/2") {
                    call.respondText("ok, ${call.sessions.get<TestUserSession>()?.userId}")
                }
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionId)}")
            }.let { call ->
                assertEquals("ok, id2", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                //                addHeader(HttpHeaders.Cookie, "$cookieName=$sessionId")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(brokenSession)}")
            }.let { call ->
                assertEquals("ok, null", call.response.content)
            }
        }
    }

    @Test
    fun testMultipleSessions() {
        val sessionStorage = SessionStorageMemory()

        withTestApplication {
            application.install(Sessions) {
                cookie<EmptySession>("EMPTY")
                cookie<TestUserSession>(cookieName, sessionStorage) {
                    transform(SessionTransportTransformerDigest())
                }
            }

            application.routing {
                get("/1") {
                    call.sessions.set(TestUserSession("id2", emptyList()))
                    call.sessions.set(EmptySession())
                    assertFailsWith<IllegalArgumentException> {
                        call.sessions.set("string")
                    }
                    call.respondText("ok")
                }
                get("/2") {
                    val userSession = call.sessions.get<TestUserSession>()
                    val emptySession = call.sessions.get<EmptySession>()
                    call.respondText("ok, ${userSession?.userId}, ${emptySession != null}")
                }
            }

            var sessionId = ""
            handleRequest(HttpMethod.Get, "/1").let { call ->
                val sessionCookie = call.response.cookies[cookieName]
                assertNotNull(sessionCookie, "No session cookie found")
                sessionId = sessionCookie!!.value
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(sessionId)}")
                addHeader(HttpHeaders.Cookie, "EMPTY=")
            }.let { call ->
                assertEquals("ok, id2, true", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                addHeader(HttpHeaders.Cookie, "EMPTY=")
            }.let { call ->
                assertEquals("ok, null, true", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
            }.let { call ->
                assertEquals("ok, null, false", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/2") {
                val brokenSession = sessionId.mapIndexed { i, c -> if (i == sessionId.lastIndex) 'x' else c }.joinToString("")
                addHeader(HttpHeaders.Cookie, "$cookieName=${encodeURLQueryComponent(brokenSession)}")
            }.let { call ->
                assertEquals("ok, null, false", call.response.content)
            }
        }
    }
}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)
data class TestUserSessionB(val userId: String, val cart: List<String>)
