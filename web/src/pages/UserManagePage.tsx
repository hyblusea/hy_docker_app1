import { useState, useEffect, useCallback } from 'react'
import { Button, Table, App, Modal, Input, Select, Space } from 'antd'
import { PlusOutlined, DeleteOutlined, EyeOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons'
import { listUsers, updateUserStatus, deleteUser, createUser, getUserPassword, type AuthUser } from '../api/auth'
import styles from './UserManagePage.module.css'

const UserManagePage = () => {
  const { message, modal } = App.useApp()
  const [users, setUsers] = useState<AuthUser[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [newUsername, setNewUsername] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newRole, setNewRole] = useState('user')

  const fetchUsers = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    try {
      const list = await listUsers(signal)
      setUsers(list)
    } catch (e: any) {
      if (signal?.aborted) return
      message.error(e.message || '获取用户列表失败')
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [message])

  useEffect(() => {
    const ac = new AbortController()
    fetchUsers(ac.signal)
    return () => ac.abort()
  }, [fetchUsers])

  const handleStatusChange = async (id: number, status: string) => {
    try {
      await updateUserStatus(id, status)
      message.success('状态更新成功')
      fetchUsers()
    } catch (e: any) {
      message.error(e.message || '状态更新失败')
    }
  }

  const handleDelete = (id: number, username: string) => {
    modal.confirm({
      title: '确认删除',
      content: `确定要删除用户 "${username}" 吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteUser(id)
          message.success('删除成功')
          fetchUsers()
        } catch (e: any) {
          message.error(e.message || '删除失败')
        }
      },
    })
  }

  const handleViewPassword = async (id: number, username: string) => {
    try {
      const pwd = await getUserPassword(id)
      Modal.info({
        title: `${username} 的密码`,
        content: <Input.Password readOnly value={pwd} style={{ marginTop: 8 }} />,
        okText: '关闭',
      })
    } catch (e: any) {
      message.error(e.message || '获取密码失败')
    }
  }

  const handleCreate = async () => {
    if (!newUsername.trim()) {
      message.warning('请输入用户名')
      return
    }
    const pwdPattern = /^(?=.*[a-zA-Z])(?=.*\d).{6,}$/
    if (!pwdPattern.test(newPassword)) {
      message.warning('密码必须大于等于6位，且必须包含字母和数字')
      return
    }
    try {
      await createUser(newUsername.trim(), newPassword, newRole)
      message.success('创建成功')
      setCreateOpen(false)
      setNewUsername('')
      setNewPassword('')
      setNewRole('user')
      fetchUsers()
    } catch (e: any) {
      message.error(e.message || '创建失败')
    }
  }

  const statusMap: Record<string, { label: string; className: string }> = {
    approved: { label: '已通过', className: styles.statusApproved },
    pending: { label: '待审核', className: styles.statusPending },
    rejected: { label: '已拒绝', className: styles.statusRejected },
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
    },
    {
      title: '用户名',
      dataIndex: 'username',
      width: 220,
    },
    {
      title: '角色',
      dataIndex: 'role',
      width: 100,
      render: (role: string) => (
        <span className={role === 'root' ? styles.roleRoot : styles.roleUser}>
          {role === 'root' ? 'ROOT' : 'USER'}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (status: string) => {
        const info = statusMap[status] || { label: status, className: '' }
        return <span className={info.className}>{info.label}</span>
      },
    },
    {
      title: '注册时间',
      dataIndex: 'created_at',
      width: 180,
      render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      width: 260,
      render: (_: unknown, record: AuthUser) => (
        <Space size={4}>
          {record.status === 'pending' && (
            <>
              <Button size="small" type="primary" icon={<CheckCircleOutlined />}
                onClick={() => handleStatusChange(record.id, 'approved')}>
                通过
              </Button>
              <Button size="small" danger icon={<CloseCircleOutlined />}
                onClick={() => handleStatusChange(record.id, 'rejected')}>
                拒绝
              </Button>
            </>
          )}
          {record.status === 'rejected' && (
            <Button size="small" type="primary" icon={<CheckCircleOutlined />}
              onClick={() => handleStatusChange(record.id, 'approved')}>
              通过
            </Button>
          )}
          {record.status === 'approved' && record.role !== 'root' && (
            <Button size="small" icon={<CloseCircleOutlined />}
              onClick={() => handleStatusChange(record.id, 'rejected')}>
              禁用
            </Button>
          )}
          <Button size="small" icon={<EyeOutlined />}
            onClick={() => handleViewPassword(record.id, record.username)}>
            密码
          </Button>
          {record.role !== 'root' && (
            <Button size="small" danger icon={<DeleteOutlined />}
              onClick={() => handleDelete(record.id, record.username)}>
              删除
            </Button>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.title}>用户管理</span>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          新建用户
        </Button>
      </div>
      <div className={styles.tableWrap}>
        <Table
          dataSource={users}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
          size="small"
        />
      </div>

      <Modal
        title="新建用户"
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => { setCreateOpen(false); setNewUsername(''); setNewPassword(''); setNewRole('user') }}
        okText="创建"
        cancelText="取消"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 12 }}>
          <Input placeholder="用户名（邮箱）" value={newUsername} onChange={(e) => setNewUsername(e.target.value)} />
          <Input.Password placeholder="密码（≥6位，含字母和数字）" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
          <Select value={newRole} onChange={setNewRole} options={[{ value: 'user', label: '普通用户' }, { value: 'root', label: '管理员' }]} />
        </div>
      </Modal>
    </div>
  )
}

export default UserManagePage
