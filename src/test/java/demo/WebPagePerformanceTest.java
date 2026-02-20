package demo;
 
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v144.network.Network;
import org.openqa.selenium.devtools.v144.network.model.ConnectionType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.*;
 
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
 
public class WebPagePerformanceTest {
 
    private ChromeDriver driver;         // ChromeDriver so we can use DevTools easily
    private JavascriptExecutor js;
 
    @BeforeMethod
    public void setup() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        // For CI machines (optional):
        // options.addArguments("--headless=new");
 
        driver = new ChromeDriver(options);
        js = (JavascriptExecutor) driver;
    }
 
    // ✅ Test 1: Measure normal page load performance using Navigation Timing API
    @Test
    public void measurePerformance_normalNetwork() {
        String url = "https://the-internet.herokuapp.com/?t=" + System.currentTimeMillis();
 
        driver.get(url);
        waitForDocumentReady();
 
        Map<String, Object> metrics = readNavigationTimingMetrics();
        printMetrics("NORMAL NETWORK", metrics);
 
        // Keep assertions very safe (test should not fail randomly)
        Assert.assertNotNull(metrics, "Metrics should not be null");
        Assert.assertTrue(getDouble(metrics, "loadMs") > 0, "Load time should be > 0");
        Assert.assertNotNull(driver.getTitle(), "Title should exist");
    }
 
    // ✅ Test 2: Measure performance under slow network (CDP network throttling)
    @SuppressWarnings("deprecation")
	@Test
    public void measurePerformance_slowNetwork_3G() {
        DevTools devTools = driver.getDevTools();
        devTools.createSession();
 
        // Enable Network domain
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));
 
        // Disable cache to make performance comparison clearer
        devTools.send(Network.setCacheDisabled(true));
 
        // Emulate slow 3G-like conditions
        // Units: latency in ms, throughputs in BYTES/SECOND
        double latencyMs = 200d;
        double downloadBytesPerSec = 150 * 1024 / 8.0; // ~150 kbps
        double uploadBytesPerSec   =  50 * 1024 / 8.0; // ~50 kbps
 
        devTools.send(Network.emulateNetworkConditions(
                false,                                   // offline
                latencyMs,                               // latency (ms)
                downloadBytesPerSec,                     // download throughput (bytes/sec)
                uploadBytesPerSec,                       // upload throughput (bytes/sec)
                Optional.of(ConnectionType.CELLULAR3G),  // connection type
                Optional.empty(),                        // maxTotalBufferSize (Number) - optional
                Optional.empty(),                        // maxResourceBufferSize (Integer) - optional
                Optional.empty()                         // enableDownloadThrottling (Boolean) - optional
        ));
 
        String url = "https://the-internet.herokuapp.com/?t=" + System.currentTimeMillis();
 
        driver.get(url);
        waitForDocumentReady();
 
        Map<String, Object> metrics = readNavigationTimingMetrics();
        printMetrics("SLOW NETWORK (3G)", metrics);
 
        Assert.assertNotNull(metrics, "Metrics should not be null");
        Assert.assertTrue(getDouble(metrics, "loadMs") > 0, "Load time should be > 0");
 
        // (Optional) If you want to stop throttling afterwards, you can either:
        // - Close the DevTools session, or
        // - Re-emulate back to normal conditions and/or re-enable cache.
        // devTools.send(Network.setCacheDisabled(false));
        // devTools.send(Network.emulateNetworkConditions(false, 0d, -1d, -1d, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    }
 
    // ---------------- HELPER METHODS ----------------
 
    // Wait until the browser says page is fully loaded
    private void waitForDocumentReady() {
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(d -> "complete".equals(js.executeScript("return document.readyState")));
        // Optional: also wait until the title is non-empty (helps on some pages)
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.not(ExpectedConditions.titleIs("")));
    }
 
    // Read timings from Performance API (Navigation Timing)
    // Standard: performance.getEntriesByType('navigation')[0]
    @SuppressWarnings("unchecked")
    private Map<String, Object> readNavigationTimingMetrics() {
        Object result = js.executeScript(
                "const nav = performance.getEntriesByType('navigation')[0];" +
                "if (!nav) return null;" +
                "return {" +
                "  dnsMs: (nav.domainLookupEnd - nav.domainLookupStart)," +
                "  tcpMs: (nav.connectEnd - nav.connectStart)," +
                "  ttfbMs: (nav.responseStart - nav.requestStart)," +
                "  responseMs: (nav.responseEnd - nav.responseStart)," +
                "  domContentLoadedMs: (nav.domContentLoadedEventEnd - nav.startTime)," +
                "  loadMs: (nav.loadEventEnd - nav.startTime)" +
                "};"
        );
        return (Map<String, Object>) result;
    }
 
    private void printMetrics(String label, Map<String, Object> m) {
        System.out.println("\n==============================");
        System.out.println("WEB PAGE PERFORMANCE: " + label);
        System.out.println("==============================");
 
        System.out.printf("DNS Lookup       : %.2f ms%n", getDouble(m, "dnsMs"));
        System.out.printf("TCP Connect      : %.2f ms%n", getDouble(m, "tcpMs"));
        System.out.printf("TTFB             : %.2f ms%n", getDouble(m, "ttfbMs"));
        System.out.printf("Response Download: %.2f ms%n", getDouble(m, "responseMs"));
        System.out.printf("DOM ContentLoaded: %.2f ms%n", getDouble(m, "domContentLoadedMs"));
        System.out.printf("Full Page Load   : %.2f ms%n", getDouble(m, "loadMs"));
    }
 
    private double getDouble(Map<String, Object> m, String key) {
        if (m == null) return 0.0;
        Object val = m.get(key);
        return val == null ? 0.0 : ((Number) val).doubleValue();
    }
 
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {
            }
        }
    
}}