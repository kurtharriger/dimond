(ns dimond.impl.component
  (:require [com.stuartsierra.component :as component]))

(defrecord InjectableComponent [start stop state]
  component/Lifecycle
  (start [this] (when-let [start (get this :start)] (start this)))
  (stop [this] (when-let [stop (get this :stop)] (stop this))))

(defn get-state-container [component] (:state component))