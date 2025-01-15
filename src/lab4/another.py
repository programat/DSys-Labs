import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

# Подготовка данных
data = [
    # Send/Recv
    {"exchangeType": "Send/Recv", "vectorSize": 100, "seqTimeNanos": 834, "parTimeNanos": 8964500, "speedup": 0.0001},
    {"exchangeType": "Send/Recv", "vectorSize": 10000, "seqTimeNanos": 10708, "parTimeNanos": 4954791,
     "speedup": 0.0022},
    {"exchangeType": "Send/Recv", "vectorSize": 100000, "seqTimeNanos": 218500, "parTimeNanos": 7114041,
     "speedup": 0.0307},
    {"exchangeType": "Send/Recv", "vectorSize": 1000000, "seqTimeNanos": 1164875, "parTimeNanos": 29424417,
     "speedup": 0.0396},
    {"exchangeType": "Send/Recv", "vectorSize": 5000000, "seqTimeNanos": 7182917, "parTimeNanos": 79368584,
     "speedup": 0.0905},
    {"exchangeType": "Send/Recv", "vectorSize": 10000000, "seqTimeNanos": 10146042, "parTimeNanos": 138211500,
     "speedup": 0.0734},

    # Sync Send/Recv
    {"exchangeType": "Sync Send/Recv", "vectorSize": 100, "seqTimeNanos": 208, "parTimeNanos": 3794458,
     "speedup": 0.0001},
    {"exchangeType": "Sync Send/Recv", "vectorSize": 10000, "seqTimeNanos": 10291, "parTimeNanos": 2771708,
     "speedup": 0.0037},
    {"exchangeType": "Sync Send/Recv", "vectorSize": 100000, "seqTimeNanos": 169250, "parTimeNanos": 2199166,
     "speedup": 0.0770},
    {"exchangeType": "Sync Send/Recv", "vectorSize": 1000000, "seqTimeNanos": 1346083, "parTimeNanos": 19826333,
     "speedup": 0.0679},
    {"exchangeType": "Sync Send/Recv", "vectorSize": 5000000, "seqTimeNanos": 4694833, "parTimeNanos": 61201125,
     "speedup": 0.0767},
    {"exchangeType": "Sync Send/Recv", "vectorSize": 10000000, "seqTimeNanos": 10259750, "parTimeNanos": 100302958,
     "speedup": 0.1023},

    # Ready Send/Recv
    {"exchangeType": "Ready Send/Recv", "vectorSize": 100, "seqTimeNanos": 667, "parTimeNanos": 5541084,
     "speedup": 0.0001},
    {"exchangeType": "Ready Send/Recv", "vectorSize": 10000, "seqTimeNanos": 10500, "parTimeNanos": 1946375,
     "speedup": 0.0054},
    {"exchangeType": "Ready Send/Recv", "vectorSize": 100000, "seqTimeNanos": 102583, "parTimeNanos": 3144250,
     "speedup": 0.0326},
    {"exchangeType": "Ready Send/Recv", "vectorSize": 1000000, "seqTimeNanos": 1363959, "parTimeNanos": 32003000,
     "speedup": 0.0426},
    {"exchangeType": "Ready Send/Recv", "vectorSize": 5000000, "seqTimeNanos": 4750875, "parTimeNanos": 53780042,
     "speedup": 0.0883},
    {"exchangeType": "Ready Send/Recv", "vectorSize": 10000000, "seqTimeNanos": 10198834, "parTimeNanos": 151466292,
     "speedup": 0.0673},

    # Buffered Send/Recv
    {"exchangeType": "Buffered Send/Recv", "vectorSize": 100, "seqTimeNanos": 2084, "parTimeNanos": 4050209,
     "speedup": 0.0005},
    {"exchangeType": "Buffered Send/Recv", "vectorSize": 10000, "seqTimeNanos": 42500, "parTimeNanos": 1626583,
     "speedup": 0.0261},
    {"exchangeType": "Buffered Send/Recv", "vectorSize": 100000, "seqTimeNanos": 165791, "parTimeNanos": 2200583,
     "speedup": 0.0753}
]

# Создание DataFrame
df = pd.DataFrame(data)

# Настройка стиля
sns.set_style("whitegrid")
colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728']

# График времени выполнения
plt.figure(figsize=(12, 6))
for i, ex_type in enumerate(df['exchangeType'].unique()):
    data = df[df['exchangeType'] == ex_type]
    plt.plot(data['vectorSize'], data['parTimeNanos'],
             marker='o', label=ex_type, color=colors[i])

plt.xscale('log')
plt.yscale('log')
plt.title('Время выполнения для разных типов обмена')
plt.xlabel('Размер вектора')
plt.ylabel('Время выполнения (нс)')
plt.grid(True, which="both", ls="-", alpha=0.2)
plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
plt.tight_layout()
plt.savefig('execution_time.png', dpi=300, bbox_inches='tight')

# График ускорения
plt.figure(figsize=(12, 6))
for i, ex_type in enumerate(df['exchangeType'].unique()):
    data = df[df['exchangeType'] == ex_type]
    plt.plot(data['vectorSize'], data['speedup'],
             marker='o', label=ex_type, color=colors[i])

plt.xscale('log')
plt.title('Ускорение для разных типов обмена')
plt.xlabel('Размер вектора')
plt.ylabel('Ускорение')
plt.grid(True, which="both", ls="-", alpha=0.2)
plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
plt.tight_layout()
plt.savefig('speedup.png', dpi=300, bbox_inches='tight')

# Создание таблицы
pivot_table = df.pivot_table(
    values=['seqTimeNanos', 'parTimeNanos', 'speedup'],
    index=['vectorSize'],
    columns=['exchangeType'],
    aggfunc='first'
)

# Сохранение таблицы в CSV
pivot_table.to_csv('results_table.csv')

plt.show()
