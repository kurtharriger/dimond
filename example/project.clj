(defproject example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [kurtharriger/dimond "0.1.0-SNAPSHOT"]
                 [com.stuartsierra/component "1.0.0"]
                 [http-kit "2.3.0"]
                 [ring/ring-core "1.9.4"]
                 [spootnik/signal "0.2.4"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]}}
  :main example.core
  :repl-options {:init-ns example.core})
