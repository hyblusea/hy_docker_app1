import { useState } from 'react'
import { CloseOutlined, RightOutlined } from '@ant-design/icons'
import styles from './HistoryTags.module.css'
import type { HistoryTag } from '../../types'

interface HistoryTagsProps {
  queries: HistoryTag[]
  onSelect: (tag: HistoryTag) => void
  onRemove: (index: number) => void
}

const HistoryTags = ({ queries, onSelect, onRemove }: HistoryTagsProps) => {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className={styles.container}>
      <div className={styles.header} onClick={() => setExpanded(v => !v)}>
        <div className={styles.headerTitle}>
          <RightOutlined className={expanded ? styles.headerIconOpen : styles.headerIcon} />
          <span>历史查询</span>
        </div>
        {queries.length > 0 && !expanded && (
          <span className={styles.headerSummary}>{queries.length}条记录</span>
        )}
      </div>
      {expanded && (
        <div className={styles.tags}>
          {queries.length === 0 ? (
            <span className={styles.empty}>暂无历史查询记录</span>
          ) : (
            queries.map((q, i) => (
              <span
                key={`${q.tsCode}-${i}`}
                className={styles.tag}
                onClick={() => onSelect(q)}
                title={`${q.name} (${q.tsCode})`}
              >
                {q.name}
                <span
                  className={styles.tagClose}
                  onClick={(e) => {
                    e.stopPropagation()
                    onRemove(i)
                  }}
                >
                  <CloseOutlined />
                </span>
              </span>
            ))
          )}
        </div>
      )}
    </div>
  )
}

export default HistoryTags
