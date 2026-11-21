;; hello.clj
;;
;; Prints a greeting built from the text of the selected node and returns it
;; as the script result. Acts on: the node the script runs on (`node`).
;; Reads only; it changes nothing in the map.
;;
;; A node with empty text gives "Hello from Clojure, !".

(let [greeting (str "Hello from Clojure, " (fp/text node) "!")]
  (println greeting)
  greeting)
