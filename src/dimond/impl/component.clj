(ns dimond.impl.component
  (:require [com.stuartsierra.component :as component]))

(defrecord InjectableComponent [start stop]
  component/Lifecycle
  (start [this] (when-let [start (-> this meta ::start)] (start this)))
  (stop [this] (when-let [stop (-> this meta ::stop)] (stop this))))