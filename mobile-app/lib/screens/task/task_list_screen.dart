import 'package:flutter/material.dart';
import '../../utils/app_theme.dart';
import '../../models/work_order.dart';
import '../../services/auth_service.dart';
import '../../services/connectivity_service.dart';
import '../task/task_detail_screen.dart';

class TaskListScreen extends StatefulWidget {
  final WorkOrder workOrder;

  const TaskListScreen({
    super.key,
    required this.workOrder,
  });

  @override
  State<TaskListScreen> createState() => _TaskListScreenState();
}

class _TaskListScreenState extends State<TaskListScreen> {
  final AuthService _authService = AuthService();
  final ConnectivityService _connectivityService = ConnectivityService();
  late List<Task> _tasks;
  bool _isRefreshing = false;
  bool _isProcessing = false;

  @override
  void initState() {
    super.initState();
    _tasks = List.from(widget.workOrder.tasks);
  }

  bool get isNewTask => widget.workOrder.type == 'new';

  Future<void> _refreshTasks() async {
    setState(() {
      _isRefreshing = true;
    });

    try {
      final isConnected = await _connectivityService.isConnected();
      
      if (!isConnected) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Sin conexión'),
              backgroundColor: AppTheme.darkRed,
            ),
          );
        }
        return;
      }

      final result = await _authService.refreshData();
      
      if (result['success'] == true) {
        final orders = widget.workOrder.type == 'team'
            ? await _authService.getTeamWorkOrders()
            : widget.workOrder.type == 'new'
                ? await _authService.getNewWorkOrders()
                : await _authService.getPersonalWorkOrders();

        for (var order in orders) {
          if (order.id == widget.workOrder.id) {
            if (mounted) {
              setState(() {
                _tasks = order.tasks;
              });
            }
            break;
          }
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(result['message'] ?? 'Error al actualizar'),
              backgroundColor: AppTheme.darkRed,
            ),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Error al actualizar'),
            backgroundColor: AppTheme.darkRed,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isRefreshing = false;
        });
      }
    }
  }

  Future<void> _acceptTask(Task task) async {
    setState(() {
      _isProcessing = true;
    });

    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      final result = await _authService.acceptTask(task.id);

      if (result['success'] == true) {
        setState(() {
          _tasks.removeWhere((t) => t.id == task.id);
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Tarea aceptada'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(result['message'] ?? 'Error al aceptar'),
              backgroundColor: AppTheme.darkRed,
            ),
          );
        }
      }
    } else {
      await _connectivityService.addToQueue('accept', task.id);
      setState(() {
        _tasks.removeWhere((t) => t.id == task.id);
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Sin conexión. Guardado.'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isProcessing = false;
    });
  }

  Future<void> _rejectTask(Task task) async {
    final reasonController = TextEditingController();
    
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.darkGrey,
        title: const Text('Rechazar Tarea', style: TextStyle(color: AppTheme.white)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              'Ingrese el motivo del rechazo:',
              style: TextStyle(color: AppTheme.lightGrey),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: reasonController,
              maxLines: 3,
              decoration: const InputDecoration(
                hintText: 'Motivo del rechazo...',
                hintStyle: TextStyle(color: AppTheme.grey),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () {
              if (reasonController.text.trim().isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('El motivo es obligatorio'),
                    backgroundColor: AppTheme.darkRed,
                  ),
                );
                return;
              }
              Navigator.pop(context, true);
            },
            child: const Text('Rechazar', style: TextStyle(color: AppTheme.lightRed)),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() {
      _isProcessing = true;
    });

    final reason = reasonController.text.trim();
    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      final result = await _authService.rejectTask(task.id, reason);

      if (result['success'] == true) {
        setState(() {
          _tasks.removeWhere((t) => t.id == task.id);
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Tarea rechazada'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(result['message'] ?? 'Error al rechazar'),
              backgroundColor: AppTheme.darkRed,
            ),
          );
        }
      }
    } else {
      await _connectivityService.addToQueue('reject', task.id, reason: reason);
      setState(() {
        _tasks.removeWhere((t) => t.id == task.id);
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Sin conexión. Guardado.'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isProcessing = false;
    });
  }

  Future<void> _acceptAllTasks() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.darkGrey,
        title: const Text('Aceptar Todas', style: TextStyle(color: AppTheme.white)),
        content: Text(
          '¿Está seguro que desea aceptar todas las tareas (${_tasks.length})?',
          style: const TextStyle(color: AppTheme.lightGrey),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Aceptar Todas', style: TextStyle(color: Colors.green)),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() {
      _isProcessing = true;
    });

    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      final result = await _authService.acceptAllTasks(widget.workOrder.id);

      if (result['success'] == true) {
        setState(() {
          _tasks.clear();
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('${result['tasksProcessed']} tareas aceptadas'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(result['message'] ?? 'Error al aceptar tareas'),
              backgroundColor: AppTheme.darkRed,
            ),
          );
        }
      }
    } else {
      // Agregar cada tarea a la cola
      for (var task in _tasks) {
        await _connectivityService.addToQueue('accept', task.id);
      }
      setState(() {
        _tasks.clear();
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Sin conexión. ${_tasks.length} acciones guardadas.'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isProcessing = false;
    });
  }

  Future<void> _rejectAllTasks() async {
    final reasonController = TextEditingController();
    
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.darkGrey,
        title: const Text('Rechazar Todas', style: TextStyle(color: AppTheme.white)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              '¿Está seguro que desea rechazar todas las tareas (${_tasks.length})?',
              style: const TextStyle(color: AppTheme.lightGrey),
            ),
            const SizedBox(height: 16),
            const Text(
              'Ingrese el motivo del rechazo:',
              style: TextStyle(color: AppTheme.lightGrey),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: reasonController,
              maxLines: 3,
              decoration: const InputDecoration(
                hintText: 'Motivo del rechazo...',
                hintStyle: TextStyle(color: AppTheme.grey),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () {
              if (reasonController.text.trim().isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('El motivo es obligatorio'),
                    backgroundColor: AppTheme.darkRed,
                  ),
                );
                return;
              }
              Navigator.pop(context, true);
            },
            child: const Text('Rechazar Todas', style: TextStyle(color: AppTheme.lightRed)),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() {
      _isProcessing = true;
    });

    final reason = reasonController.text.trim();
    final isConnected = await _connectivityService.isConnected();

    if (isConnected) {
      final result = await _authService.rejectAllTasks(
        widget.workOrder.id,
        reason,
      );

      if (result['success'] == true) {
        setState(() {
          _tasks.clear();
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('${result['tasksProcessed']} tareas rechazadas'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(result['message'] ?? 'Error al rechazar tareas'),
              backgroundColor: AppTheme.darkRed,
            ),
          );
        }
      }
    } else {
      // Agregar cada tarea a la cola
      for (var task in _tasks) {
        await _connectivityService.addToQueue('reject', task.id, reason: reason);
      }
      final count = _tasks.length;
      setState(() {
        _tasks.clear();
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Sin conexión. $count acciones guardadas.'),
            backgroundColor: AppTheme.primaryRed,
          ),
        );
      }
    }

    setState(() {
      _isProcessing = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('${widget.workOrder.tag} - Tareas'),
        actions: [
          if (isNewTask && _tasks.isNotEmpty) ...[
            IconButton(
              icon: const Icon(Icons.check_circle, color: Colors.green),
              onPressed: _isProcessing ? null : _acceptAllTasks,
              tooltip: 'Aceptar todas',
            ),
            IconButton(
              icon: const Icon(Icons.cancel, color: AppTheme.lightRed),
              onPressed: _isProcessing ? null : _rejectAllTasks,
              tooltip: 'Rechazar todas',
            ),
          ],
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refreshTasks,
        color: AppTheme.primaryRed,
        child: _tasks.isEmpty
            ? ListView(
                children: [
                  SizedBox(
                    height: MediaQuery.of(context).size.height * 0.7,
                    child: const Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.task_outlined,
                            size: 64,
                            color: AppTheme.grey,
                          ),
                          SizedBox(height: 16),
                          Text(
                            'No hay tareas en esta orden',
                            style: TextStyle(
                              color: AppTheme.grey,
                              fontSize: 18,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              )
            : ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: _tasks.length,
                itemBuilder: (context, index) {
                  final task = _tasks[index];
                  return _buildTaskCard(context, task);
                },
              ),
      ),
    );
  }

  Widget _buildTaskCard(BuildContext context, Task task) {
    final isCompleted = task.status == 'completed';
    
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: () async {
          final result = await Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => TaskDetailScreen(
                task: task,
                taskType: widget.workOrder.type,
              ),
            ),
          );
          if (result == true && mounted) {
            _refreshTasks();
          }
        },
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header with status
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: isCompleted 
                          ? AppTheme.darkGrey 
                          : isNewTask 
                              ? Colors.orange 
                              : AppTheme.primaryRed,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      isCompleted ? 'Completado' : task.orderNumber,
                      style: TextStyle(
                        color: isCompleted ? AppTheme.grey : AppTheme.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 12,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  
                  if (task.dispatchType.isNotEmpty)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: AppTheme.darkGrey,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        task.dispatchType,
                        style: const TextStyle(
                          color: AppTheme.lightGrey,
                          fontSize: 11,
                        ),
                      ),
                    ),
                  const Spacer(),
                  
                  if (task.dueDate.isNotEmpty)
                    Text(
                      task.dueDate,
                      style: TextStyle(
                        color: isCompleted ? AppTheme.grey : AppTheme.lightRed,
                        fontSize: 12,
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              
              // Task title
              Text(
                task.title,
                style: TextStyle(
                  color: isCompleted ? AppTheme.grey : AppTheme.white,
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                  decoration: isCompleted ? TextDecoration.lineThrough : null,
                ),
              ),
              
              if (task.description.isNotEmpty) ...[
                const SizedBox(height: 4),
                Text(
                  task.description,
                  style: TextStyle(
                    color: isCompleted ? AppTheme.darkGrey : AppTheme.lightGrey,
                    fontSize: 14,
                  ),
                ),
              ],
              const SizedBox(height: 12),
              
              // Location info
              Row(
                children: [
                  const Icon(Icons.location_on, size: 16, color: AppTheme.grey),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      '${task.location} / ${task.department}',
                      style: const TextStyle(
                        color: AppTheme.grey,
                        fontSize: 12,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              
              // Machine info
              Row(
                children: [
                  const Icon(Icons.settings, size: 16, color: AppTheme.grey),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      '${task.machine} / ${task.machinePart}',
                      style: const TextStyle(
                        color: AppTheme.grey,
                        fontSize: 12,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
              
              if (task.product.isNotEmpty) ...[
                const SizedBox(height: 4),
                Row(
                  children: [
                    const Icon(Icons.oil_barrel, size: 16, color: AppTheme.grey),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        task.product,
                        style: const TextStyle(
                          color: AppTheme.grey,
                          fontSize: 12,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ],

              // Accept/Reject buttons for new tasks
              if (isNewTask && !isCompleted) ...[
                const SizedBox(height: 12),
                const Divider(color: AppTheme.darkGrey),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    TextButton.icon(
                      onPressed: _isProcessing ? null : () => _acceptTask(task),
                      icon: const Icon(Icons.check, size: 16, color: Colors.green),
                      label: const Text('Aceptar', style: TextStyle(color: Colors.green)),
                    ),
                    const SizedBox(width: 8),
                    TextButton.icon(
                      onPressed: _isProcessing ? null : () => _rejectTask(task),
                      icon: const Icon(Icons.close, size: 16, color: AppTheme.lightRed),
                      label: const Text('Rechazar', style: TextStyle(color: AppTheme.lightRed)),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
