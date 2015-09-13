(ns gpio-socket.gpio
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [gpio-socket.macros :refer [<? dochan]])
  (:require [cljs.core.async :as async]
            [cljs.nodejs :as node]
            [gpio-socket.utils :as utils]))

(def onoff (js/require "onoff"))

(def writes (async/chan))
(def changes (async/chan))
(def errors (async/chan))
(def changes-mult (async/mult changes))
(def gpios (atom {}))
(def watches (atom {}))

(defn create [port direction]
  (if-let [gpio (get @gpios port)]
    (do
      (.setDirection gpio direction)
      gpio)
    (let [gpio (onoff.Gpio. port direction)]
      (swap! gpios assoc port gpio)
      gpio)))

(defn watch [port]
  (let [f (get @watches port)]
    (when-not f
      (let [gpio (create port "in")
            f (fn [err value]
                (if err
                  (async/put! errors err)
                  (async/put! changes [port value])))]
        (.setEdge gpio "both")
        (.watch gpio f)
        (swap! watches assoc port f)))))

(defn unwatch [port]
  ;; TODO: Handle unwatch by counting the number
  ;; of times the port was watched
  )

(defn write [port value]
  (async/put! writes [port value]))

(go-loop []
  (when-let [[port value] (async/<! writes)]
    (let [gpio (create port "out")]
      (try
        (<? (utils/with-chan #(.write gpio value (utils/chan->cb %))))
        (catch js/Error err
          (>! errors err)))
      (recur))))

;; TODO: Notify clients of errors
(go
  (dochan [err errors]
    (println "gpio error:" err)))
