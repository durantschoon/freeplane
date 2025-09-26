;; Advanced Clojure script example for Freeplane
;; This script demonstrates more complex node manipulation and functional programming

(println "Advanced Clojure script starting...")

;; Access the current node
(def current-node node)

;; Get all children of the current node
(def children (.getChildren current-node))

;; Process children using Clojure's functional programming
(def processed-children
  (->> children
       (map #(.getText %))
       (filter #(not (empty? %)))
       (map clojure.string/upper-case)))

;; Print processed children
(println "Processed children:" processed-children)

;; Create a summary node
(def summary-node (.addChild current-node "Summary"))
(.setText summary-node (str "Found " (count processed-children) " non-empty children"))

;; Add details for each processed child
(doseq [child-text processed-children]
  (let [detail-node (.addChild summary-node "Detail")]
    (.setText detail-node child-text)))

;; Use Clojure's data manipulation
(def node-data
  {:node-id (.getId current-node)
   :text (.getText current-node)
   :child-count (count children)
   :timestamp (java.util.Date.)})

;; Create a data node
(def data-node (.addChild current-node "Node Data"))
(.setText data-node (str node-data))

(println "Advanced script completed!")
(println "Node data:" node-data)
