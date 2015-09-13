(ns gpio-socket.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [gpio-socket.macros :refer [dochan]])
  (:require [cljs.core.async :as async]
            [cljs.nodejs :as node]
            [clojure.walk :as walk]
            [gpio-socket.gpio :as gpio]
            [gpio-socket.utils :refer [conj-if dissoc-in]]))

(def ws (node/require "ws"))

(defn- filter-port
  "Returns a transducer that filters changes to
  only the given port."
  [port]
  (filter #(= (first %) port)))

(defn- change->write
  "Returns a transducer that maps changes to
  (notification) writes."
  []
  (map (fn [[port value]]
         [:change port value])))

(defn subscribe
  "Subscribe the given client to changes on the
  specified port."
  [{:keys [out subscriptions] :as client} port]
  (if (get subscriptions port)
    client
    (let [subscription (async/chan)]
      (-> gpio/changes-mult
          (async/tap subscription)
          (async/pipe (async/chan 1 (filter-port port)))
          (async/pipe (async/chan 1 (change->write)))
          (async/pipe out))
      (gpio/watch port)
      (assoc-in client [:subscriptions port] subscription))))

(defn unsubscribe
  "Unsubscribe the given client to changes on the
  specified port."
  [{:keys [subscriptions] :as client} port]
  (if-let [subscription (get subscriptions port)]
    (do
      (async/untap gpio/changes-mult subscription)
      (gpio/unwatch port)
      (dissoc-in client [:subscriptions port]))
    client))

(defn quit [{:keys [in out socket subscriptions] :as client}]
  (doseq [port (keys subscriptions)]
    (unsubscribe client port))
  (async/close! in)
  (async/close! out)
  (.close socket)
  nil)

;; messages

(defn- encode-message [data]
  (-> data
      (clj->js)
      (js/JSON.stringify)))

(defn- parse-message [message]
  (-> message
      (js/JSON.parse)
      (walk/keywordize-keys)))

(defmulti handle-message
  (fn [client message]
    (-> message
        (first)
        (keyword))))

(defmethod handle-message :subscribe
  [client [_ port]]
  (subscribe client port))

(defmethod handle-message :unsubscribe
  [client [_ port]]
  (unsubscribe client port))

(defmethod handle-message :write
  [client [_ port value]]
  (gpio/write port value)
  client)

(defmethod handle-message :quit
  [client [_]]
  (quit client))

(defmethod handle-message :default
  [{:keys [out] :as client} _]
  (async/put! out ["error" "invalid command"])
  client)

;; clients

(def clients (atom #{}))

(defn create-client [socket]
  {:in (async/chan)
   :out (async/chan)
   :socket socket
   :subscriptions {}})

(defn register-client [socket]
  (let [{:keys [in out] :as client} (create-client socket)]
    (go-loop [old-client client]
      (when-let [message (async/<! in)]
        (let [new-client (handle-message old-client message)]
          (swap! clients #(-> %
                              (disj old-client)
                              (conj-if new-client)))
          (recur new-client))))

    (go
      (dochan [data out]
        (.send socket (encode-message data))))

    (.on socket "message" #(async/put! in (try
                                            (parse-message %)
                                            (catch js/Error err
                                              (async/put! out ["error" "bad syntax"])))))
    (.on socket "close" #(async/put! in [:quit]))

    (swap! clients conj client)))

(defn listen [port]
  (let [ws (ws.Server. #js {:port port})]
    (.on ws "connection" register-client)
    ws))

;; main

(enable-console-print!)

(defn -main [&args]
  (listen 3000)
  (println "Listening on port 3000"))

(set! *main-cli-fn* -main)
