/**
Some documentation

 * @param[a] Some int
 * @param[b] String
 */
fun testMethod(a: Int, b: String) {

}

fun test() {
    <caret>testMethod(1, "value")
}

//INFO: <div class='definition'><pre>fun testMethod(a: Int, b: String): Unit</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://a"><code><span style="color:#0000ff;">a</span></code></a></code> - Some int<p><code><a href="psi_element://b"><code><span style="color:#0000ff;">b</span></code></a></code> - String</td></table><div class='bottom'><icon src="file"/>&nbsp;OnMethodUsageWithBracketsInParam.kt<br/></div>
