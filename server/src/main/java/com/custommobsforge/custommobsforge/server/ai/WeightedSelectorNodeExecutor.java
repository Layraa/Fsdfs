package com.custommobsforge.custommobsforge.server.ai;

import com.custommobsforge.custommobsforge.common.data.BehaviorNode;
import com.custommobsforge.custommobsforge.common.entity.CustomMobEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class WeightedSelectorNodeExecutor implements NodeExecutor {
    private static final Random random = new Random();

    @Override
    public boolean execute(CustomMobEntity entity, BehaviorNode node, BehaviorTreeExecutor executor) {
        // Получаем все дочерние узлы
        List<BehaviorNode> children = executor.getChildNodes(node);

        // Если нет дочерних узлов, возвращаем неудачу
        if (children.isEmpty()) {
            return false;
        }

        // Получаем параметр весов из узла
        String weightParam = node.getParameter();
        Map<String, Double> weights = parseWeights(weightParam);

        // Вычисляем общий вес
        double totalWeight = 0;
        for (BehaviorNode child : children) {
            double weight = weights.getOrDefault(child.getId(), 50.0);
            totalWeight += weight;
        }

        // Выбираем случайный узел на основе весов
        double randomValue = random.nextDouble() * totalWeight;
        double currentTotal = 0;

        for (BehaviorNode child : children) {
            double weight = weights.getOrDefault(child.getId(), 50.0);
            currentTotal += weight;

            if (randomValue <= currentTotal) {
                // Выполняем выбранный узел
                return executor.executeNode(child);
            }
        }

        // Если по какой-то причине ни один узел не выбран, выполняем первый
        return !children.isEmpty() && executor.executeNode(children.get(0));
    }

    // Парсинг параметра весов
    private Map<String, Double> parseWeights(String weightParam) {
        Map<String, Double> weights = new HashMap<>();

        if (weightParam == null || weightParam.isEmpty()) {
            return weights;
        }

        // Формат: nodeId1=weight1;nodeId2=weight2;...
        String[] pairs = weightParam.split(";");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                try {
                    String nodeId = keyValue[0].trim();
                    double weight = Double.parseDouble(keyValue[1].trim());
                    weights.put(nodeId, weight);
                } catch (NumberFormatException e) {
                    // Игнорируем ошибки парсинга
                }
            }
        }

        return weights;
    }
}