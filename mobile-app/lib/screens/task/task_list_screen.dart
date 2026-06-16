import 'package:flutter/material.dart';
import '../../utils/app_theme.dart';
import '../../models/work_order.dart';
import '../task/task_detail_screen.dart';

class TaskListScreen extends StatelessWidget {
  final WorkOrder workOrder;

  const TaskListScreen({
    super.key,
    required this.workOrder,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('${workOrder.tag} - Tareas'),
      ),
      body: workOrder.tasks.isEmpty
          ? const Center(
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
            )
          : ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: workOrder.tasks.length,
              itemBuilder: (context, index) {
                final task = workOrder.tasks[index];
                return _buildTaskCard(context, task);
              },
            ),
    );
  }

  Widget _buildTaskCard(BuildContext context, Task task) {
    final isCompleted = task.status == 'completed';
    final isNewTask = workOrder.type == 'new';
    
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: () async {
          final result = await Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => TaskDetailScreen(
                task: task,
                taskType: workOrder.type,
              ),
            ),
          );
          // If result is true, refresh the page
          if (result == true && context.mounted) {
            Navigator.pop(context, true);
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
                  // Order number or status
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
                  
                  // Dispatch type
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
                  
                  // Due date
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
              
              // Description
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
              
              // Product
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
                      onPressed: () {
                        // Navigate to detail for accept/reject
                      },
                      icon: const Icon(Icons.check, size: 16, color: Colors.green),
                      label: const Text('Aceptar', style: TextStyle(color: Colors.green)),
                    ),
                    const SizedBox(width: 8),
                    TextButton.icon(
                      onPressed: () {
                        // Navigate to detail for reject
                      },
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
