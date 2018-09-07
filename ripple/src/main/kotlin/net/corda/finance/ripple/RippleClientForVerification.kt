package net.corda.finance.ripple

import java.net.URI

/** Whoever is verifying will specify the server(s) they want to use. */
class RippleClientForVerification(configFileName: String, override val nodeUri: URI) : ReadOnlyRippleClient