import json
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns

# Загрузка данных
with open('results.json', 'r') as f:
    data = json.load(f)

# Преобразование в pandas DataFrame
df = pd.DataFrame(data)

# Нормализация времени в миллисекунды
df['seqTimeMs'] = df['seqTimeNanos'] / 1_000_000
df['parTimeMs'] = df['parTimeNanos'] / 1_000_000

# Создание графиков для каждого метода обмена
plt.figure(figsize=(15, 10))

# График зависимости времени выполнения от размера вектора
plt.subplot(2, 2, 1)
for method in df['exchangeType'].unique():
    method_data = df[df['exchangeType'] == method]
    for proc in sorted(method_data['processCount'].unique()):
        data = method_data[method_data['processCount'] == proc]
        plt.plot(data['vectorSize'], data['parTimeMs'],
                 label=f'{method} ({proc} proc)', marker='o')

plt.xscale('log')
plt.yscale('log')
plt.xlabel('Размер вектора')
plt.ylabel('Время выполнения (мс)')
plt.title('Время выполнения vs Размер вектора')
plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
plt.grid(True)

# График ускорения от количества процессов
plt.subplot(2, 2, 2)
for method in df['exchangeType'].unique():
    method_data = df[df['exchangeType'] == method]
    for size in sorted(method_data['vectorSize'].unique()):
        data = method_data[method_data['vectorSize'] == size]
        plt.plot(data['processCount'], data['speedup'],
                 label=f'{method} (size {size})', marker='o')

plt.xlabel('Количество процессов')
plt.ylabel('Ускорение')
plt.title('Ускорение vs Количество процессов')
plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
plt.grid(True)

plt.tight_layout()
plt.savefig('performance_analysis.png', bbox_inches='tight')
plt.close()

# Анализ результатов
analysis = {
    'max_speedup': df.loc[df['speedup'].idxmax()].to_dict(),
    'avg_speedup_by_method': df.groupby('exchangeType')['speedup'].mean().to_dict(),
    'avg_speedup_by_size': df.groupby('vectorSize')['speedup'].mean().to_dict(),
    'avg_speedup_by_processes': df.groupby('processCount')['speedup'].mean().to_dict()
}

print("\nАнализ производительности:")
print(f"\n1. Максимальное ускорение: {analysis['max_speedup']['speedup']:.4f}")
print(f"   - Метод: {analysis['max_speedup']['exchangeType']}")
print(f"   - Размер вектора: {analysis['max_speedup']['vectorSize']}")
print(f"   - Количество процессов: {analysis['max_speedup']['processCount']}")

print("\n2. Среднее ускорение по методам:")
for method, speedup in analysis['avg_speedup_by_method'].items():
    print(f"   - {method}: {speedup:.4f}")

print("\n3. Среднее ускорение по размерам векторов:")
for size, speedup in analysis['avg_speedup_by_size'].items():
    print(f"   - Размер {size}: {speedup:.4f}")

print("\n4. Среднее ускорение по количеству процессов:")
for proc, speedup in analysis['avg_speedup_by_processes'].items():
    print(f"   - {proc} процессов: {speedup:.4f}")