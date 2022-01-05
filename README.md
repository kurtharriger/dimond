# Dimond 

**Injectable Partial Functions** (Alpha)

*Dimond is a Dependency Injection tool for Clojure inspired by [dime](https://github.com/kumarshantanu/dime) extended to build [component](https://github.com/stuartsierra/component) systems.*

**Alpha: This was mostly hacked together during my Christmas holiday as an experiment** 

> ...I do not currently have plans to maintain this project as Clojure is no loner my day job.  I would however like to contribute some of the code or ideas back to Dime and the Clojure Community. Feel free to fork this project and carry on.  if you want to contribute to moving this forward you can  find me as `@kurtharriger` on [Clojarians Slack](https://clojurians.slack.com/) or reach me via email at [kurtharriger@gmail.com](mailto:kurtharriger@gmail.com)

This enables you to move your co-locate your dependency metadata on the functions making it possible to build your component system by providing a list of namespaces from the dependencies will be determined from your `defn` metadata.

The inspiration came form this [Blog Post](https://kumarshantanu.medium.com/dependency-injection-with-clojure-using-dime-af57b140bd3f) by the author Shantanu Kumar and some examples found in the [Dime Documentation](https://github.com/kumarshantanu/dime/blob/master/doc/intro.md).

In the short time I experimented with Dime however I found it difficult to remap dependencies before injecting them and no shutdown lifecycle for stateful components...

As I set about adding these features I started experiment with reusing some dependency code from [component](https://github.com/stuartsierra/component) and decided to just map dime graph to components allowing one to use a proven tool [component](https://github.com/stuartsierra/component) to manage lifecycles and [dime](https://github.com/kumarshantanu/dime) as a discovery tool.  

I also found that the repl experience of partial functions was not that great and needed to frequently restart the system to make changes visible... This is because partial functions were applied to the var values not the vars themselves an as such I rewrote the injection logic to preserve both the var and the value to enable instant usage of changes in the repl that are safe and simple dependency updates in place without restarting the system.

I somewhat disliked using ``((:app system) ... )`` to invoke partial functions so I created a helper function called `dimond` that makes it convenient to invoke partial functions.

This utility can be bound to an external  var or atom that you may already be using for  [component](https://github.com/stuartsierra/component) .


I also started to experimented with an event driven system using queries to fetch system state and events to mutate it allowing me to keep the logic pure despite the seemingly mutable nature of dependency injection.  

I believe that this query plus event model could be used to create plugins and intercepters to automatically decorate components when system starts or to provide default services when one is requested but not explicitly defined but non of that is implemented here yet...  Unfortunately, my holiday has come to an end for now.


## Does Clojure need a dependency injection tool?

Dependency injection can be *trivially* accomplished using a partial function to create a closure.

For example, 

In a javaish OO language you might do something like:
```
class TaxCalculator {
    private double taxRate;
    public TaxCalculator(double taxRate) {
        this.taxRate = taxRate;
    }
    public double calculateTax(double amount) {
        return amount * taxRate;
    }
}
// ...
TaxCalculator stateTaxCalculator = new TaxCalculator(0.02);
TaxCalculator localTaxCalculator = new TaxCalculator(0.03);
double stateTax = stateTaxCalculator.calculateTax(100); // 2.00
double localTax = stateTaxCalculator.calculateTax(100); // 3.00
```

In Clojure, you can close over the tax rate using partial to create a new function 

```
(defn calculate-tax [tax-rate amount] (* amount tax-rate))
(def calculate-state-tax (partial calculate-tax 0.02))
(def calculate-local-tax (partial calculate-tax 0.03))
;; ...
(calculate-state-tax 100) ;; => 2.00
(calculate-local-tax 100) ;; => 3.00
```

That's basically it... 

However as systems get bigger the wiring gets longer and more complex.  Adding a new dependency means updating both the function definition and factory functions which are often not co-located making it more difficult to change and maintain. 

So why write code when you can use data? and things that change together should be located together.  Thus function metadata seems an ideal place to express dependencies. 


## Building a dependency graph with metadata

In the following example (found in example folder) we express that our ring web handler named `app` should be partially applied with a greeter  `hello-greeter`.  

```
(ns example.minimal
  (:require [dimond.core :as di]
            [org.httpkit.server :as httpkit]))
            
(defn ^:expose hello-greeter [name]
  (str "Hello, " name))

(defn ^:expose welcome-greeter [name]
  (str "Welcome " name))

(defn ^:expose app [^{:inject :hello-greeter} greeter req]
  {:body    (greeter "World")})

;; can also define as component with lifecyclie 
(defn ^:expose start-server [^:inject app port]
  (httpkit/run-server app {:port port}))

(comment
  (def dimond (di/dimond ::di/namespaces [*ns*]))
  (dimond ::di/start)
  (def stop-server (dimond :start-server 3003))

  (dimond :app {})
  ;; try changing world to Vistors change will take effect 
  ;; immediately after redefining var, no restart required

  ;; now try changing  :hello-greeter to welcome greeter
  (dimond :app {})
  ;; you will get a warning and it will continue to use the old function until you refresh
  ;; #'example.minimal/app  dependency list has changed.  system refresh required to use new function

  (dimond ::di/refresh)
  (dimond :app {})
  )
```

See `example` project for how to add lifecycle components and to rewire dependencies when building systems.  

> Note I would recommend using namespace qualified component names using ^{:expose ::app} and ^{:inject ::app} instead to avoid potential naming colusions in larger systems.


## License

Copyright © 2021 Kurt Harriger

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
