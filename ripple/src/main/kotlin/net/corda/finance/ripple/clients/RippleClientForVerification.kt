package net.corda.finance.ripple.clients

import java.net.URI

/** Whoever is verifying will specify the server they want to use. */
class RippleClientForVerification(override val nodeUri: URI) : ReadOnlyRippleClient