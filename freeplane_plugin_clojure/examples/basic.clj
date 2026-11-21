;; basic.clj
;;
;; Reads the children and attributes of the selected node and adds one child
;; summarising them. Acts on: the node the script runs on (`node`).
;; One undo step (Ctrl+Z) removes the summary child again.
;;
;; A node without children or attributes gets "Summary: 0 children,
;; 0 attributes" and no listing lines.

(let [child-texts (mapv fp/plain-text (fp/children node))
      attributes  (fp/attrs node)
      summary     (str "Summary: " (count child-texts) " children, "
                       (count attributes) " attributes")]
  (doseq [text child-texts]
    (println "child:" text))
  (doseq [[attribute-name value] attributes]
    (println "attribute:" attribute-name "=" value))
  (fp/add-child! node summary))
