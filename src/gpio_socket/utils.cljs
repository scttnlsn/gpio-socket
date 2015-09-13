(ns gpio-socket.utils
  (:require [cljs.core.async :as async]))

(defn conj-if [coll x]
  (if x
    (conj coll x)
    coll))

(defn dissoc-in [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

;; errors

(defn error? [x]
  (instance? js/Error x))

(defn throw-error [x]
  (if (error? x)
    (throw x)
    x))

;; async

(defn chan->cb [ch]
  (fn [err value]
    (async/put! ch (or err value true))
    (async/close! ch)))

(defn with-chan [f]
  (let [ch (async/chan)]
    (f ch)
    ch))
