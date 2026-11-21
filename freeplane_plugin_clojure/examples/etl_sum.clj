;; etl_sum.clj
;;
;; Sums the numeric attribute `cost` over the selected node and all its
;; descendants, then stores the total in the attribute `total_cost` of the
;; selected node. Acts on: the node the script runs on (`node`).
;;
;; Numbers and numeric strings count. Any other `cost` value is skipped with a
;; warning in the log. Nodes without `cost` are ignored. With no `cost` at all
;; the total is 0.0 and `total_cost` is set to 0.0.

(let [parse-cost (fn [value]
                   (if (number? value)
                     (double value)
                     (try
                       (Double/parseDouble (clojure.string/trim (str value)))
                       (catch NumberFormatException _ nil))))
      entries    (->> (tree-seq (comp seq :children) :children (fp/node->map node))
                      (keep (fn [{:keys [id text attrs]}]
                              (when (contains? attrs "cost")
                                {:id id :text text :raw (get attrs "cost")
                                 :cost (parse-cost (get attrs "cost"))}))))
      skipped    (remove :cost entries)
      total      (reduce + 0.0 (keep :cost entries))]
  (doseq [{:keys [id text raw]} skipped]
    (fp/warn "etl_sum: skipping non-numeric cost" (pr-str raw) "on node" id (pr-str text)))
  (fp/set-attr! node "total_cost" total)
  total)
