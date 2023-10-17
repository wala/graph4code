class Foo:
    def foo(self, i, j):
        return i + j, j + 1

f = Foo()
a, b = f.foo(20,j=22)

print(a)
print(b)
