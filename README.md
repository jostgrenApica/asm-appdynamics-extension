Apica ASM - AppDynamics Java Monitor Extension
=======================================
Links
-------------------
* From AppDynamics documentation [Build an monitor extension](https://docs.appdynamics.com/display/PRO42/Build+a+Monitoring+Extension+Using+Java)

Information
-------------------
This AppDynamics Extension will fetch data (response time) from Apica WebPerformance Monitoring (WPM) API and integrate them into the Metric Browser inside of AppDynamics. 

The purpose of the integration is to build Dashboards in AppDynamics which contains check data from Apica ASM.

## Build the extension in your Java Maven project
1. Do a mvn build. This is only needed if change the monitor extension source code. 
2. Your target jar file will be placed in the monitor directory
3. The monitor directory will also contain a template for the monitor.xml which are used for building the extension on the AppDynamics machine Agent .


Installation
-------------------
* Requires the AppDynamics Machine Agent
* Requires AppDynamics with support for extensions
* Requires an account in [Apica ASM](https://www.apica.io/)

1. Copy the created .jar file and monitor.xml from the monitor folder in project to the /Monitors/apica folder in the installation directory of the Machine Agent
2. Set the required values in monitor.xml
3. Start the Machine Agent and it will scan the /Monitor/ folder and execute the Jar file with arguments it finds in monitor.xml
4. Observe the MachineAgent/Log/agent.log
5. Metrics should be popping up in the Metric Browser inside of AppDynamics for the application associated to the machine agent. 

Notes (testing jar)
-------------------
* Input: 2 args. (AuthTicket, BaseApiUrl)
* 
* You can also call the .jar file.  >java -jar apicaAppDynamicsMetrics<version>.jar - and output should arrive to the console (stdout)

## Parameters in monitor.xml
**AuthTicket** - Auth ticket for Apica ASM API. See API documentation

**BaseApiUrl** - The base URL for Apica ASM API

**TagName** - Tag name used for filtering and grouping of checks. This parameter is optional, but we encourage you to use it. Use a tag like 'Application' or 'Business Service' for grouping the checks

**Proxy settings**
Monitor.xml also contains optional proxy setting parameters. They are used if need a proxy for the https connection to Apica ASM API end-point. 

``` 
<monitor>
	<name>ApicaMonitor</name>
	<type>managed</type>
	<description>Reports check metrics from Apica API </description>
	<monitor-configuration></monitor-configuration>
	<monitor-run-task>
		<execution-style>periodic</execution-style>
		<!--<execution-style>periodic</execution-style> -->
		<execution-frequency-in-seconds>60</execution-frequency-in-seconds>
		<name>Apica Monitor Run Task</name>
		<display-name>Apica Monitor Task</display-name>
		<description>Apica Monitor Task</description>
		<type>java</type>
		<java-task>
			<classpath>apicaAppdynamicsMetrics-2.0-full.jar</classpath>
			<impl-class>com.apicasystems.integration.appdynamics.ApicaMonitor
			</impl-class>
		</java-task>
		<task-arguments>
			<!-- AuthTicket: You will find this value from API section of the Apica 
				Synthetic Monitoring suite. BaseApiUrl: This is the URL to the API. Should 
				end with a slash. -->
			<argument name="AuthTicket" is-required="true"
				default-value="{replace this with apica api auth_ticket uuid}" />

			<!-- Do not change manually unless you have on-prem installation -->
			<argument name="BaseApiUrl" is-required="true"
				default-value="https://api-wpm.apicasystem.com/v3" />
			<!-- Do not change manually unless you have on-prem installation -->
			<argument name="TagName" is-required="false"
				default-value="" />
			<!-- Optional proxy settings for connection ASM -->
			<argument name="ProxyHost" is-required="false"
				default-value="" />
			<argument name="ProxyPort" is-required="false"
				default-value="" />
			<!-- http (default) or https -->
			<argument name="ProxyScheme" is-required="false"
				default-value="" />

			<!-- CONFIGURE METRIC PATH You can change the metric path where to store 
				the checks and their values under. The default is: Custom Metrics|Apica -->
			<argument name="Metric-Path" is-required="false"
				default-value="Custom Metrics|Apica ASM" />
		</task-arguments>
	</monitor-run-task>
</monitor>

```

## AppDynamics Metrics per check

**value**
The value of the check. Often the total response time. Can sometimes be mapped to another thing

**status**
A code for the severity. Need to use code due to that AppDynamics metrics must be numeric values.
* 0 = Fatal or Unknown (F,U)
* 1 = Error (E)
* 2 = Warning (W)
* 3 = Information (I)

**sla_fulfillment**
A calculation of the SLA fulfillment from the check result
sla_percent_current_month/target_sla * 100 rounded to integer. It means that it can contain a value > 100. Exact on target will produce 100.





