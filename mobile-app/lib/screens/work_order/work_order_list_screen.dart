import 'package:flutter/material.dart';
import '../../utils/app_theme.dart';
import '../../models/work_order.dart';
import '../task/task_list_screen.dart';

class WorkOrderListScreen extends StatelessWidget {
  final String title;
  final List<WorkOrder> workOrders;
  final String type;

  const WorkOrderListScreen({
    super.key,
    required this.title,
    required this.workOrders,
    required this.type,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
      ),
      body: workOrders.isEmpty
          ? const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.assignment_outlined,
                    size: 64,
                    color: AppTheme.grey,
                  ),
                  SizedBox(height: 16),
                  Text(
                    'No hay órdenes de trabajo',
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
              itemCount: workOrders.length,
              itemBuilder: (context, index) {
                final order = workOrders[index];
                return _buildWorkOrderCard(context, order);
              },
            ),
    );
  }

  Widget _buildWorkOrderCard(BuildContext context, WorkOrder order) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => TaskListScreen(
                workOrder: order,
              ),
            ),
          );
        },
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryRed,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      order.tag,
                      style: const TextStyle(
                        color: AppTheme.white,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      order.title,
                      style: const TextStyle(
                        color: AppTheme.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              
              // Due date
              if (order.dueDate.isNotEmpty)
                Row(
                  children: [
                    const Icon(Icons.calendar_today, size: 16, color: AppTheme.lightGrey),
                    const SizedBox(width: 8),
                    Text(
                      order.dueDate,
                      style: const TextStyle(color: AppTheme.lightGrey),
                    ),
                  ],
                ),
              const SizedBox(height: 8),
              
              // Task count and completion
              Row(
                children: [
                  const Icon(Icons.task_alt, size: 16, color: AppTheme.lightGrey),
                  const SizedBox(width: 8),
                  Text(
                    '${order.taskCount} tareas',
                    style: const TextStyle(color: AppTheme.lightGrey),
                  ),
                  const SizedBox(width: 16),
                  if (order.completionStatus.isNotEmpty)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: AppTheme.darkGrey,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        order.completionStatus,
                        style: const TextStyle(
                          color: AppTheme.lightGrey,
                          fontSize: 12,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              
              // View tasks button
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Text(
                    'Ver tareas',
                    style: TextStyle(
                      color: AppTheme.primaryRed,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Icon(
                    Icons.arrow_forward,
                    size: 16,
                    color: AppTheme.primaryRed,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
