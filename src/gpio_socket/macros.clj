(ns gpio-socket.macros)

(defmacro <? [expr]
  `(gpio-socket.utils/throw-error (cljs.core.async/<! ~expr)))

(defmacro dochan [[binding ch] & body]
  `(loop []
     (when-let [~binding (<? ~ch)]
       ~@body
       (recur))))
