;; Basic Clojure script example for Freeplane
;; This script demonstrates basic node manipulation

(println "Hello from Clojure!")

;; Access the current node
(def current-node node)

;; Get the node text
(def node-text (str current-node))

;; Print information about the current node
(println "Current node:" node-text)

;; Add a child node
(.addChild current-node "Hello from Clojure!")

;; Add another child with more complex content
(def new-child (.addChild current-node "Clojure Script Result"))
(.setText new-child (str "Script executed at: " (java.util.Date.)))

(println "Script execution completed!")
