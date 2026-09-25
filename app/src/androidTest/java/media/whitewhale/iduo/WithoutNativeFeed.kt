package media.whitewhale.iduo

import org.junit.rules.ExternalResource

/** Pager/layout tests use the real Discover page with the remote window transport disabled.
 * LiveDiscoverIntegrationTest separately exercises Google's real, independently focused window.
 */
class WithoutNativeFeed : ExternalResource() {
    override fun before() { LiveDiscover.attachNativeFeed = false }
    override fun after() { LiveDiscover.attachNativeFeed = true }
}
