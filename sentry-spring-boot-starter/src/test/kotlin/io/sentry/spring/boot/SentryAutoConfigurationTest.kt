package io.sentry.spring.boot

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.check
import io.sentry.core.Breadcrumb
import io.sentry.core.IHub
import io.sentry.core.Sentry
import io.sentry.core.SentryEvent
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import io.sentry.core.transport.ITransport
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class SentryAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java, WebMvcAutoConfiguration::class.java))

    private val webContextRunner = WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java))

    @Test
    fun `hub is not created when auto-configuration is disabled`() {
        contextRunner.withPropertyValues("sentry.enabled=false", "sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).doesNotHaveBean(IHub::class.java)
            }
    }

    @Test
    fun `hub is created when auto-configuration is enabled`() {
        contextRunner.withPropertyValues("sentry.enabled=true", "sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(IHub::class.java)
            }
    }

    @Test
    fun `hub is created when dsn is provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(IHub::class.java)
            }
    }

    @Test
    fun `hub is not created when dsn is provided but sentry is disabled`() {
        contextRunner.withPropertyValues("sentry.enabled=false", "sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).doesNotHaveBean(IHub::class.java)
            }
    }

    @Test
    fun `OptionsConfiguration is created if custom one is not provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(Sentry.OptionsConfiguration::class.java)
            }
    }

    @Test
    fun `OptionsConfiguration is not created if custom one is provided`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomOptionsConfigurationConfiguration::class.java)
            .run {
                assertThat(it).hasSingleBean(Sentry.OptionsConfiguration::class.java)
                assertThat(it.getBean(Sentry.OptionsConfiguration::class.java, "customOptionsConfiguration")).isNotNull
            }
    }

    @Test
    fun `properties are applied to SentryOptions`() {
        contextRunner.withPropertyValues(
            "sentry.dsn=http://key@localhost/proj",
            "sentry.read-timeout-millis=10",
            "sentry.shutdown-timeout-millis=20",
            "sentry.flush-timeout-millis=30",
            "sentry.bypass-security=true",
            "sentry.debug=true",
            "sentry.diagnostic-level=INFO",
            "sentry.sentry-client-name=my-client",
            "sentry.max-breadcrumbs=100",
            "sentry.release=1.0.3",
            "sentry.environment=production",
            "sentry.sample-rate=0.2",
            "sentry.in-app-excludes[0]=org.springframework",
            "sentry.in-app-includes[0]=com.myapp",
            "sentry.dist=my-dist",
            "sentry.attach-threads=true",
            "sentry.attach-stacktrace=true",
            "sentry.server-name=host-001"
        ).run {
            val options = it.getBean(SentryOptions::class.java)
            assertThat(options.readTimeoutMillis).isEqualTo(10)
            assertThat(options.shutdownTimeout).isEqualTo(20)
            assertThat(options.flushTimeoutMillis).isEqualTo(30)
            assertThat(options.isBypassSecurity).isTrue()
            assertThat(options.isDebug).isTrue()
            assertThat(options.diagnosticLevel).isEqualTo(SentryLevel.INFO)
            assertThat(options.maxBreadcrumbs).isEqualTo(100)
            assertThat(options.release).isEqualTo("1.0.3")
            assertThat(options.environment).isEqualTo("production")
            assertThat(options.sampleRate).isEqualTo(0.2)
            assertThat(options.inAppExcludes).containsOnly("org.springframework")
            assertThat(options.inAppIncludes).containsOnly("com.myapp")
            assertThat(options.dist).isEqualTo("my-dist")
            assertThat(options.isAttachThreads).isEqualTo(true)
            assertThat(options.isAttachStacktrace).isEqualTo(true)
            assertThat(options.serverName).isEqualTo("host-001")
        }
    }

    @Test
    fun `sets sentryClientName property on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it.getBean(SentryOptions::class.java).sentryClientName).isEqualTo("sentry.java.spring-boot")
            }
    }

    @Test
    fun `does not register event processors for non web-servlet application type`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).doesNotHaveBean(SentryRequestHttpServletRequestProcessor::class.java)
                assertThat(it).doesNotHaveBean(SentryUserHttpServletRequestProcessor::class.java)
            }
    }

    @Test
    fun `registers event processors for web servlet application type`() {
        webContextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(it).hasSingleBean(SentryRequestHttpServletRequestProcessor::class.java)
                assertThat(it).hasSingleBean(SentryUserHttpServletRequestProcessor::class.java)
            }
    }

    @Test
    fun `sets SDK version on sent events`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(MockTransportConfiguration::class.java)
            .run {
                Sentry.captureMessage("Some message")
                val transport = it.getBean(ITransport::class.java)
                verify(transport).send(check { event: SentryEvent ->
                    assertThat(event.sdk.version).isEqualTo(BuildConfig.VERSION_NAME)
                    assertThat(event.sdk.name).isEqualTo(BuildConfig.SENTRY_SPRING_BOOT_SDK_NAME)
                    assertThat(event.sdk.packages).anyMatch { pkg ->
                        pkg.name == "maven:sentry-spring-boot-starter" && pkg.version == BuildConfig.VERSION_NAME }
                })
            }
    }

    @Test
    fun `registers beforeSendCallback on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomBeforeSendCallbackConfiguration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).beforeSend).isInstanceOf(CustomBeforeSendCallback::class.java)
            }
    }

    @Test
    fun `registers beforeBreadcrumbCallback on SentryOptions`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withUserConfiguration(CustomBeforeBreadcrumbCallbackConfiguration::class.java)
            .run {
                assertThat(it.getBean(SentryOptions::class.java).beforeBreadcrumb).isInstanceOf(CustomBeforeBreadcrumbCallback::class.java)
            }
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomOptionsConfigurationConfiguration {

        @Bean
        open fun customOptionsConfiguration() = Sentry.OptionsConfiguration<SentryOptions>() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    open class MockTransportConfiguration {

        @Bean
        open fun sentryTransport() = mock<ITransport>()
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomBeforeSendCallbackConfiguration {

        @Bean
        open fun beforeSendCallback() = CustomBeforeSendCallback()
    }

    class CustomBeforeSendCallback : SentryOptions.BeforeSendCallback {
        override fun execute(event: SentryEvent, hint: Any?): SentryEvent? = null
    }

    @Configuration(proxyBeanMethods = false)
    open class CustomBeforeBreadcrumbCallbackConfiguration {

        @Bean
        open fun beforeBreadcrumbCallback() = CustomBeforeBreadcrumbCallback()
    }

    class CustomBeforeBreadcrumbCallback : SentryOptions.BeforeBreadcrumbCallback {
        override fun execute(breadcrumb: Breadcrumb, hint: Any?): Breadcrumb? = null
    }
}
