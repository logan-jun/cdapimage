.. meta::
    :author: Cask Data, Inc.
    :copyright: Copyright © 2014 Cask Data, Inc.

.. _operations-metrics:

=======
Metrics
=======

.. highlight:: java

As applications process data, the CDAP collects metrics about the application’s behavior
and performance. Some of these metrics are the same for every application—how many events
are processed, how many data operations are performed—and are thus called system or CDAP
metrics.

Other metrics are user-defined or "custom" and differ from application to application.
To add user-defined metrics to your application, read this section in conjunction with the
details on available system metrics in the :ref:`Metrics HTTP RESTful API <http-restful-api-metrics>`.

You embed user-defined metrics in the methods defining the components of your application.
The metrics system currently supports two kinds of metrics: **count** and **gauge**:

- **count:** Increments (or decrements) the metric named *metricName* by delta::

    count(metricName, delta)

- **gauge:** Sets the metric named *metricName* to a specific value::

    gauge(metricName, value)

They will then emit their metrics and you can retrieve them (along with system metrics)
via the *Metrics Explorer* in the :ref:`CDAP UI <cdap-ui>` or
via the CDAP’s :ref:`restful-api`. The names given to the metrics (such as
``names.longnames`` and ``names.bytes`` as in the example below) should be composed only
of alphanumeric characters.

To add a count metric to a flowlet *NameSaver*::

  public static class NameSaver extends AbstractFlowlet {
    static final byte[] NAME = { 'n', 'a', 'm', 'e' };

    @UseDataSet("whom")
    KeyValueTable whom;
    Metrics flowletMetrics; // Declare the custom metrics

    @ProcessInput
    public void processInput(StreamEvent event) {
      byte[] name = Bytes.toBytes(event.getBody());
      if (name != null && name.length > 0) {
        whom.write(NAME, name);
      }
      if (name.length > 10) {
        flowletMetrics.count("names.longnames", 1);
      }
      flowletMetrics.count("names.bytes", name.length);
    }
  }

To add a gauge metric to the flowlet *WordProcessor*::

  public class WordProcessor extends AbstractFlowlet {
    OutputEmitter<String> output;
    Metrics flowletMetrics; // Declare the custom metrics
    int longestNameSeen = 0;

    @ProcessInput
    public void processInput(String name) {
      flowletMetrics.count("name.length", name.length());
      if (name.length() > longestNameSeen) {
        longestNameSeen = name.length();
        flowletMetrics.gauge("name.longest", longestNameSeen);
      }
      output.emit(name);
    }
  }

Under high load, metrics system may overload HBase. To mitigate the effect, Metrics system also provides an option to
enable/disable metrics using `app.program.metrics.enabled` parameter which can have values as `true` or `false`.
By default, program metrics are enabled, so the default value for this parameter is `true`.
This option can be configured at 3 different levels:

- **System wide** In cdap-site.xml (system wide) to control whether app containers emit metrics
- **Per Run** Using the config `system.metrics.enabled` as program argument to override the system-wide setting per program run.
- **Program Preference** Using the `system.metrics.enabled` permanently for a program by setting a preference.
- **Scoping** Additionally for program types that have sub-components (such as flows, MapReduce and Spark programs),
  a prefix can be added to `system.metrics.enabled` to limit the scope of the arguments. :ref:`Configuring Sub-components <advanced-configuring-resources>`.

Using Metrics Explorer
----------------------
The *Metrics Explorer* of the :ref:`CDAP UI <cdap-ui>`
can be used to examine and set metrics in a CDAP instance.
